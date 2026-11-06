package com.rathan.riskmonitor.aws;

import com.rathan.riskmonitor.aws.config.AwsProperties;
import com.rathan.riskmonitor.aws.dynamodb.DynamoDbDeduplicationService;
import com.rathan.riskmonitor.aws.s3.S3IngestionService;
import com.rathan.riskmonitor.transaction.TransactionService;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3IngestionServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private DynamoDbDeduplicationService deduplicationService;

    @Mock
    private TransactionService transactionService;

    private AwsProperties properties;
    private S3IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        properties = new AwsProperties();
        properties.getS3().setEnabled(true);
        properties.getS3().setBucketName("test-bucket");
        properties.getS3().setIncomingPrefix("incoming/");
        properties.getS3().setProcessedPrefix("processed/");
        properties.getS3().setFailedPrefix("failed/");
        properties.getS3().setWorkerThreads(2);

        ingestionService = new S3IngestionService(s3Client, deduplicationService, transactionService, properties);
    }

    @AfterEach
    void tearDown() {
        ingestionService.shutdown();
    }

    @Test
    void testInitBucketIfNotExists_WhenBucketMissing_CreatesBucket() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("Bucket missing").build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());

        ingestionService.initBucketIfNotExists("test-bucket");

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void testPollAndIngest_SubmitsAvailableBatches() throws Exception {
        S3Object obj1 = S3Object.builder().key("incoming/").size(0L).build(); // Directory marker, should skip
        S3Object obj2 = S3Object.builder().key("incoming/batch1.csv").eTag("\"etag123\"").size(250L).build();

        ListObjectsV2Response listResp = ListObjectsV2Response.builder()
                .contents(List.of(obj1, obj2))
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResp);
        when(deduplicationService.acquireLease(eq("incoming/batch1.csv:etag123"), any())).thenReturn(true);

        String csvData = "id,account_id,merchant_id,device_id,amount,currency,country,txn_time,channel\n" +
                "1001,10,20,30,150.00,USD,US,2026-11-06T10:00:00Z,ONLINE\n";

        ResponseInputStream<GetObjectResponse> s3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)))
        );

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3Stream);
        when(transactionService.ingestBatchIdempotent(any())).thenReturn(new BulkTransactionResponse(1, 0));

        ingestionService.pollAndIngest();

        // Allow worker pool to finish task
        Thread.sleep(300);

        verify(deduplicationService).acquireLease(eq("incoming/batch1.csv:etag123"), any());
        verify(transactionService).ingestBatchIdempotent(any());
        verify(deduplicationService).markDone(eq("incoming/batch1.csv:etag123"), eq(1), eq(0));
        verify(s3Client).copyObject(argThat((CopyObjectRequest req) ->
                req.destinationKey().equals("processed/batch1.csv")
        ));
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                req.key().equals("incoming/batch1.csv")
        ));
    }

    @Test
    void testPollAndIngest_WhenLeaseNotAcquired_SkipsBatch() {
        S3Object obj = S3Object.builder().key("incoming/locked.csv").eTag("etag456").size(100L).build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of(obj)).build());
        when(deduplicationService.acquireLease(eq("incoming/locked.csv:etag456"), any())).thenReturn(false);

        ingestionService.pollAndIngest();

        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void testProcessBatch_JsonFormat() throws Exception {
        String jsonData = "[\n" +
                "  {\"id\": 2001, \"accountId\": 1, \"merchantId\": 2, \"deviceId\": 3, \"amount\": 250.00, \"currency\": \"USD\", \"country\": \"US\", \"txnTime\": \"2026-11-06T10:00:00Z\", \"channel\": \"POS\"}\n" +
                "]";

        ResponseInputStream<GetObjectResponse> s3Stream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8)))
        );

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3Stream);
        when(transactionService.ingestBatchIdempotent(any())).thenReturn(new BulkTransactionResponse(1, 1));

        ingestionService.processBatch("incoming/data.json", "incoming/data.json:etag-json");

        verify(deduplicationService).markDone("incoming/data.json:etag-json", 1, 1);
        verify(s3Client).copyObject(argThat((CopyObjectRequest req) ->
                req.destinationKey().equals("processed/data.json")
        ));
    }

    @Test
    void testProcessBatch_FailureHandling_MovesToFailed() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 read error"));

        ingestionService.processBatch("incoming/corrupt.csv", "incoming/corrupt.csv:etag-err");

        verify(deduplicationService).markFailed(eq("incoming/corrupt.csv:etag-err"), eq("S3 read error"));
        verify(s3Client).copyObject(argThat((CopyObjectRequest req) ->
                req.destinationKey().equals("failed/corrupt.csv")
        ));
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                req.key().equals("incoming/corrupt.csv")
        ));
    }

    @Test
    void testParseBatchStream_NdJson() throws Exception {
        String ndJson = "{\"id\": 3001, \"accountId\": 1, \"merchantId\": 2, \"deviceId\": 3, \"amount\": 99.00, \"currency\": \"USD\", \"country\": \"US\", \"txnTime\": \"2026-11-06T10:00:00Z\", \"channel\": \"ATM\"}\n" +
                "{\"id\": 3002, \"accountId\": 1, \"merchantId\": 2, \"deviceId\": 3, \"amount\": 199.00, \"currency\": \"USD\", \"country\": \"US\", \"txnTime\": \"2026-11-06T10:05:00Z\", \"channel\": \"ATM\"}\n";

        ByteArrayInputStream is = new ByteArrayInputStream(ndJson.getBytes(StandardCharsets.UTF_8));
        List<TransactionRequest> list = ingestionService.parseBatchStream(is, "data.json");

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo(3001L);
        assertThat(list.get(1).getId()).isEqualTo(3002L);
    }
}

