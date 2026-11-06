package com.rathan.riskmonitor.aws;

import com.rathan.riskmonitor.aws.config.AwsProperties;
import com.rathan.riskmonitor.aws.dynamodb.DynamoDbDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbDeduplicationServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private AwsProperties properties;
    private DynamoDbDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        properties = new AwsProperties();
        properties.getDynamodb().setEnabled(true);
        properties.getDynamodb().setTableName("test_ingestion_jobs");
        properties.getDynamodb().setLeaseDurationSeconds(60);

        deduplicationService = new DynamoDbDeduplicationService(dynamoDbClient, properties);
    }

    @Test
    void testInitTableIfNotExists_WhenTableExists() {
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(DescribeTableResponse.builder().build());

        deduplicationService.initTableIfNotExists();

        verify(dynamoDbClient).describeTable(any(DescribeTableRequest.class));
        verify(dynamoDbClient, never()).createTable(any(CreateTableRequest.class));
    }

    @Test
    void testInitTableIfNotExists_WhenTableNotFound_CreatesTable() {
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("Table not found").build());
        when(dynamoDbClient.createTable(any(CreateTableRequest.class)))
                .thenReturn(CreateTableResponse.builder().build());

        deduplicationService.initTableIfNotExists();

        verify(dynamoDbClient).createTable(any(CreateTableRequest.class));
    }

    @Test
    void testAcquireLease_FreshBatch_Succeeds() {
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        boolean acquired = deduplicationService.acquireLease("incoming/batch1.csv:etag123", "worker-1");

        assertThat(acquired).isTrue();
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        verify(dynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void testAcquireLease_ExistingBatch_ReclaimExpiredLease_Succeeds() {
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("Item exists").build());
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        boolean acquired = deduplicationService.acquireLease("incoming/batch1.csv:etag123", "worker-2");

        assertThat(acquired).isTrue();
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void testAcquireLease_ExistingBatch_ActiveLeaseHeld_Fails() {
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("Item exists").build());
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("Lease not expired").build());

        boolean acquired = deduplicationService.acquireLease("incoming/batch1.csv:etag123", "worker-3");

        assertThat(acquired).isFalse();
    }

    @Test
    void testMarkDone() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        deduplicationService.markDone("batch-1", 100, 5);

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("test_ingestion_jobs") &&
                req.key().get("batch_key").s().equals("batch-1") &&
                req.expressionAttributeValues().get(":done").s().equals("DONE") &&
                req.expressionAttributeValues().get(":rec_cnt").n().equals("100") &&
                req.expressionAttributeValues().get(":alt_cnt").n().equals("5")
        ));
    }

    @Test
    void testMarkFailed() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        deduplicationService.markFailed("batch-1", "Parsing error at line 4");

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("test_ingestion_jobs") &&
                req.expressionAttributeValues().get(":failed").s().equals("FAILED") &&
                req.expressionAttributeValues().get(":err_msg").s().contains("Parsing error")
        ));
    }

    @Test
    void testIsBatchProcessed_WhenDone() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "batch_key", AttributeValue.fromS("batch-1"),
                        "status", AttributeValue.fromS("DONE")
                ))
                .build());

        assertThat(deduplicationService.isBatchProcessed("batch-1")).isTrue();
    }

    @Test
    void testIsBatchProcessed_WhenInProgressOrMissing() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "batch_key", AttributeValue.fromS("batch-2"),
                        "status", AttributeValue.fromS("IN_PROGRESS")
                ))
                .build());

        assertThat(deduplicationService.isBatchProcessed("batch-2")).isFalse();

        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertThat(deduplicationService.isBatchProcessed("batch-missing")).isFalse();
    }

    @Test
    void testDisabledDynamoDb_BypassesCalls() {
        properties.getDynamodb().setEnabled(false);

        deduplicationService.initTableIfNotExists();
        boolean acquired = deduplicationService.acquireLease("any", "worker");
        deduplicationService.markDone("any", 1, 1);
        deduplicationService.markFailed("any", "err");
        boolean processed = deduplicationService.isBatchProcessed("any");

        assertThat(acquired).isTrue();
        assertThat(processed).isFalse();
        verifyNoInteractions(dynamoDbClient);
    }
}

