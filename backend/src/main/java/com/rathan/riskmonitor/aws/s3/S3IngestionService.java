package com.rathan.riskmonitor.aws.s3;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rathan.riskmonitor.aws.config.AwsProperties;
import com.rathan.riskmonitor.aws.dynamodb.DynamoDbDeduplicationService;
import com.rathan.riskmonitor.transaction.TransactionService;
import com.rathan.riskmonitor.transaction.dto.BulkTransactionResponse;
import com.rathan.riskmonitor.transaction.dto.TransactionRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class S3IngestionService {

    private static final Logger log = LoggerFactory.getLogger(S3IngestionService.class);

    private final S3Client s3Client;
    private final DynamoDbDeduplicationService deduplicationService;
    private final TransactionService transactionService;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool;

    public S3IngestionService(
            S3Client s3Client,
            DynamoDbDeduplicationService deduplicationService,
            TransactionService transactionService,
            AwsProperties awsProperties
    ) {
        this.s3Client = s3Client;
        this.deduplicationService = deduplicationService;
        this.transactionService = transactionService;
        this.awsProperties = awsProperties;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        int threads = Math.max(1, awsProperties.getS3().getWorkerThreads());
        this.workerPool = Executors.newFixedThreadPool(threads);
    }

    @PostConstruct
    public void init() {
        if (!awsProperties.getS3().isEnabled()) {
            log.info("S3 ingestion is disabled by configuration.");
            return;
        }

        try {
            initBucketIfNotExists(awsProperties.getS3().getBucketName());
            deduplicationService.initTableIfNotExists();
        } catch (Exception e) {
            log.warn("Could not verify S3 bucket or DynamoDB table at startup: {}", e.getMessage());
        }
    }

    public void initBucketIfNotExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            log.info("Bucket {} does not exist. Creating bucket...", bucketName);
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Bucket {} created successfully.", bucketName);
            } catch (Exception ex) {
                log.warn("Could not create bucket {}: {}", bucketName, ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("Could not check bucket {}: {}", bucketName, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down S3 ingestion worker pool...");
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(15, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Scheduled(fixedDelayString = "${aws.s3.poll-rate-ms:60000}")
    public void pollAndIngest() {
        if (!awsProperties.getS3().isEnabled()) {
            return;
        }

        String bucket = awsProperties.getS3().getBucketName();
        String incomingPrefix = awsProperties.getS3().getIncomingPrefix();

        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(incomingPrefix)
                    .build());

            List<S3Object> objects = response.contents();
            if (objects == null || objects.isEmpty()) {
                return;
            }

            for (S3Object s3Object : objects) {
                String key = s3Object.key();
                if (key.endsWith("/") || s3Object.size() == 0) {
                    continue; // Skip directory markers
                }

                String eTag = s3Object.eTag() != null ? s3Object.eTag().replace("\"", "") : "unknown";
                String batchKey = key + ":" + eTag;
                String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

                boolean acquired = deduplicationService.acquireLease(batchKey, workerId);
                if (!acquired) {
                    log.debug("Batch {} lease not acquired, skipping.", batchKey);
                    continue;
                }

                // Submit batch processing to worker pool
                workerPool.submit(() -> processBatch(key, batchKey));
            }
        } catch (Exception e) {
            log.warn("Error during S3 pollAndIngest: {}", e.getMessage());
        }
    }

    public void processBatch(String s3Key, String batchKey) {
        String bucket = awsProperties.getS3().getBucketName();
        log.info("Processing batch {} from S3 key {}", batchKey, s3Key);

        try {
            ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());

            List<TransactionRequest> requests = parseBatchStream(inputStream, s3Key);
            log.info("Parsed {} transactions from batch {}", requests.size(), batchKey);

            BulkTransactionResponse resp = transactionService.ingestBatchIdempotent(requests);
            deduplicationService.markDone(batchKey, resp.getIngestedCount(), resp.getAlertsGenerated());

            String fileName = s3Key.contains("/") ? s3Key.substring(s3Key.lastIndexOf('/') + 1) : s3Key;
            String processedKey = awsProperties.getS3().getProcessedPrefix() + fileName;
            moveObject(s3Key, processedKey);

            log.info("Successfully ingested batch {}: {} transactions, {} alerts generated",
                    batchKey, resp.getIngestedCount(), resp.getAlertsGenerated());
        } catch (Exception e) {
            log.error("Failed to process batch {}: {}", batchKey, e.getMessage(), e);
            deduplicationService.markFailed(batchKey, e.getMessage());

            try {
                String fileName = s3Key.contains("/") ? s3Key.substring(s3Key.lastIndexOf('/') + 1) : s3Key;
                String failedKey = awsProperties.getS3().getFailedPrefix() + fileName;
                moveObject(s3Key, failedKey);
            } catch (Exception moveEx) {
                log.error("Failed to move batch {} to failed prefix: {}", batchKey, moveEx.getMessage());
            }
        }
    }

    public List<TransactionRequest> parseBatchStream(InputStream inputStream, String key) throws Exception {
        if (key.toLowerCase().endsWith(".json")) {
            return parseJson(inputStream);
        } else {
            return parseCsv(inputStream);
        }
    }

    private List<TransactionRequest> parseJson(InputStream is) throws Exception {
        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        if (content.startsWith("[")) {
            TransactionRequest[] array = objectMapper.readValue(content, TransactionRequest[].class);
            return Arrays.asList(array);
        } else {
            // Line-delimited JSON
            List<TransactionRequest> list = new ArrayList<>();
            for (String line : content.split("\\r?\\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    list.add(objectMapper.readValue(line, TransactionRequest.class));
                }
            }
            return list;
        }
    }

    private List<TransactionRequest> parseCsv(InputStream is) throws Exception {
        List<TransactionRequest> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return list;
            }

            String[] headers = headerLine.split(",");
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim().toLowerCase().replace("_", ""), i);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",");

                TransactionRequest req = new TransactionRequest();

                if (colIndex.containsKey("id") && parts.length > colIndex.get("id")) {
                    String val = parts[colIndex.get("id")].trim();
                    if (!val.isEmpty()) {
                        req.setId(Long.parseLong(val));
                    }
                }
                if (colIndex.containsKey("accountid") && parts.length > colIndex.get("accountid")) {
                    req.setAccountId(Long.parseLong(parts[colIndex.get("accountid")].trim()));
                }
                if (colIndex.containsKey("merchantid") && parts.length > colIndex.get("merchantid")) {
                    req.setMerchantId(Long.parseLong(parts[colIndex.get("merchantid")].trim()));
                }
                if (colIndex.containsKey("deviceid") && parts.length > colIndex.get("deviceid")) {
                    req.setDeviceId(Long.parseLong(parts[colIndex.get("deviceid")].trim()));
                }
                if (colIndex.containsKey("amount") && parts.length > colIndex.get("amount")) {
                    req.setAmount(new BigDecimal(parts[colIndex.get("amount")].trim()));
                }
                if (colIndex.containsKey("currency") && parts.length > colIndex.get("currency")) {
                    req.setCurrency(parts[colIndex.get("currency")].trim());
                }
                if (colIndex.containsKey("country") && parts.length > colIndex.get("country")) {
                    req.setCountry(parts[colIndex.get("country")].trim());
                }
                if (colIndex.containsKey("txntime") && parts.length > colIndex.get("txntime")) {
                    String ts = parts[colIndex.get("txntime")].trim();
                    req.setTxnTime(Instant.parse(ts));
                }
                if (colIndex.containsKey("channel") && parts.length > colIndex.get("channel")) {
                    req.setChannel(parts[colIndex.get("channel")].trim());
                }

                list.add(req);
            }
        }
        return list;
    }

    private void moveObject(String sourceKey, String destinationKey) {
        String bucket = awsProperties.getS3().getBucketName();
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(destinationKey)
                .build());

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(sourceKey)
                .build());
    }

    public ExecutorService getWorkerPool() {
        return workerPool;
    }
}
