package com.rathan.riskmonitor.aws.dynamodb;

import com.rathan.riskmonitor.aws.config.AwsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class DynamoDbDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbDeduplicationService.class);

    private final DynamoDbClient dynamoDbClient;
    private final AwsProperties awsProperties;

    public DynamoDbDeduplicationService(DynamoDbClient dynamoDbClient, AwsProperties awsProperties) {
        this.dynamoDbClient = dynamoDbClient;
        this.awsProperties = awsProperties;
    }

    public void initTableIfNotExists() {
        if (!awsProperties.getDynamodb().isEnabled()) {
            return;
        }

        String tableName = awsProperties.getDynamodb().getTableName();
        try {
            dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
            log.info("DynamoDB table {} exists and is accessible.", tableName);
        } catch (ResourceNotFoundException e) {
            log.info("Creating DynamoDB table {} for ingestion jobs deduplication...", tableName);
            try {
                dynamoDbClient.createTable(CreateTableRequest.builder()
                        .tableName(tableName)
                        .attributeDefinitions(AttributeDefinition.builder()
                                .attributeName("batch_key")
                                .attributeType(ScalarAttributeType.S)
                                .build())
                        .keySchema(KeySchemaElement.builder()
                                .attributeName("batch_key")
                                .keyType(KeyType.HASH)
                                .build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .build());
                log.info("DynamoDB table {} created successfully.", tableName);
            } catch (Exception ex) {
                log.warn("Could not create DynamoDB table {}: {}", tableName, ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("Could not check DynamoDB table {}: {}", tableName, e.getMessage());
        }
    }

    public boolean acquireLease(String batchKey, String workerId) {
        if (!awsProperties.getDynamodb().isEnabled()) {
            return true;
        }

        String tableName = awsProperties.getDynamodb().getTableName();
        long now = Instant.now().getEpochSecond();
        long expiry = now + awsProperties.getDynamodb().getLeaseDurationSeconds();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("batch_key", AttributeValue.fromS(batchKey));
        item.put("status", AttributeValue.fromS("IN_PROGRESS"));
        item.put("worker_id", AttributeValue.fromS(workerId));
        item.put("lease_expiry", AttributeValue.fromN(String.valueOf(expiry)));
        item.put("created_at", AttributeValue.fromS(Instant.now().toString()));
        item.put("updated_at", AttributeValue.fromS(Instant.now().toString()));

        // Step 1: Attempt initial claim for brand new batch
        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(batch_key)")
                    .build());
            log.info("Acquired fresh lease on batch {} for worker {}", batchKey, workerId);
            return true;
        } catch (ConditionalCheckFailedException e) {
            log.debug("Batch {} already exists, attempting to reclaim expired lease if applicable", batchKey);
        } catch (Exception e) {
            log.error("Failed to acquire lease for batch {}: {}", batchKey, e.getMessage());
            return false;
        }

        // Step 2: Attempt reclaiming expired lease from crashed worker
        try {
            Map<String, String> expressionNames = new HashMap<>();
            expressionNames.put("#status", "status");
            expressionNames.put("#worker", "worker_id");
            expressionNames.put("#expiry", "lease_expiry");
            expressionNames.put("#updated", "updated_at");

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":in_progress", AttributeValue.fromS("IN_PROGRESS"));
            expressionValues.put(":worker_id", AttributeValue.fromS(workerId));
            expressionValues.put(":new_expiry", AttributeValue.fromN(String.valueOf(expiry)));
            expressionValues.put(":now_str", AttributeValue.fromS(Instant.now().toString()));
            expressionValues.put(":now", AttributeValue.fromN(String.valueOf(now)));

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("batch_key", AttributeValue.fromS(batchKey)))
                    .updateExpression("SET #status = :in_progress, #worker = :worker_id, #expiry = :new_expiry, #updated = :now_str")
                    .conditionExpression("#status = :in_progress AND #expiry < :now")
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues)
                    .build());

            log.info("Reclaimed expired lease on batch {} for worker {}", batchKey, workerId);
            return true;
        } catch (ConditionalCheckFailedException e) {
            log.info("Batch {} is currently locked or already completed by another worker. Skipping.", batchKey);
            return false;
        } catch (Exception e) {
            log.error("Failed to reclaim lease on batch {}: {}", batchKey, e.getMessage());
            return false;
        }
    }

    public void markDone(String batchKey, int recordCount, int alertCount) {
        if (!awsProperties.getDynamodb().isEnabled()) {
            return;
        }

        String tableName = awsProperties.getDynamodb().getTableName();
        try {
            Map<String, String> expressionNames = new HashMap<>();
            expressionNames.put("#status", "status");
            expressionNames.put("#rec", "records_processed");
            expressionNames.put("#alt", "alerts_generated");
            expressionNames.put("#updated", "updated_at");

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":done", AttributeValue.fromS("DONE"));
            expressionValues.put(":rec_cnt", AttributeValue.fromN(String.valueOf(recordCount)));
            expressionValues.put(":alt_cnt", AttributeValue.fromN(String.valueOf(alertCount)));
            expressionValues.put(":now_str", AttributeValue.fromS(Instant.now().toString()));

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("batch_key", AttributeValue.fromS(batchKey)))
                    .updateExpression("SET #status = :done, #rec = :rec_cnt, #alt = :alt_cnt, #updated = :now_str")
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues)
                    .build());

            log.info("Marked batch {} as DONE (records: {}, alerts: {})", batchKey, recordCount, alertCount);
        } catch (Exception e) {
            log.error("Failed to mark batch {} as DONE: {}", batchKey, e.getMessage());
        }
    }

    public void markFailed(String batchKey, String errorMessage) {
        if (!awsProperties.getDynamodb().isEnabled()) {
            return;
        }

        String tableName = awsProperties.getDynamodb().getTableName();
        try {
            Map<String, String> expressionNames = new HashMap<>();
            expressionNames.put("#status", "status");
            expressionNames.put("#err", "error_message");
            expressionNames.put("#updated", "updated_at");

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":failed", AttributeValue.fromS("FAILED"));
            expressionValues.put(":err_msg", AttributeValue.fromS(errorMessage != null ? errorMessage : "Unknown error"));
            expressionValues.put(":now_str", AttributeValue.fromS(Instant.now().toString()));

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("batch_key", AttributeValue.fromS(batchKey)))
                    .updateExpression("SET #status = :failed, #err = :err_msg, #updated = :now_str")
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues)
                    .build());

            log.info("Marked batch {} as FAILED: {}", batchKey, errorMessage);
        } catch (Exception e) {
            log.error("Failed to mark batch {} as FAILED: {}", batchKey, e.getMessage());
        }
    }

    public boolean isBatchProcessed(String batchKey) {
        if (!awsProperties.getDynamodb().isEnabled()) {
            return false;
        }

        String tableName = awsProperties.getDynamodb().getTableName();
        try {
            GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("batch_key", AttributeValue.fromS(batchKey)))
                    .build());

            if (!resp.hasItem()) {
                return false;
            }

            AttributeValue status = resp.item().get("status");
            return status != null && "DONE".equalsIgnoreCase(status.s());
        } catch (Exception e) {
            log.error("Failed to check if batch {} is processed: {}", batchKey, e.getMessage());
            return false;
        }
    }
}

