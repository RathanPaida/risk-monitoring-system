package com.rathan.riskmonitor.aws;

import com.rathan.riskmonitor.aws.config.AwsProperties;
import com.rathan.riskmonitor.aws.dynamodb.DynamoDbDeduplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcurrentWorkerDeduplicationTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Test
    @DisplayName("Two competing workers targeting the same batch: only one acquires lease")
    void testCompetingWorkersOnSameBatch() throws Exception {
        AwsProperties properties = new AwsProperties();
        properties.getDynamodb().setEnabled(true);
        properties.getDynamodb().setTableName("ingestion_jobs");

        DynamoDbDeduplicationService deduplicationService = new DynamoDbDeduplicationService(dynamoDbClient, properties);

        AtomicInteger putCallCount = new AtomicInteger(0);

        // First call to putItem succeeds, second call encounters ConditionalCheckFailedException
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenAnswer(invocation -> {
            if (putCallCount.incrementAndGet() == 1) {
                return PutItemResponse.builder().build();
            } else {
                throw ConditionalCheckFailedException.builder().message("Batch already claimed").build();
            }
        });

        // Expired lease reclaim attempt will also fail because first worker holds active lease
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("Lease not expired").build());

        String batchKey = "incoming/large_transactions_batch.csv:etag999";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Boolean> worker1 = executor.submit(() -> {
            startLatch.await();
            return deduplicationService.acquireLease(batchKey, "worker-node-1");
        });

        Future<Boolean> worker2 = executor.submit(() -> {
            startLatch.await();
            return deduplicationService.acquireLease(batchKey, "worker-node-2");
        });

        // Unleash both workers simultaneously
        startLatch.countDown();

        boolean result1 = worker1.get(5, TimeUnit.SECONDS);
        boolean result2 = worker2.get(5, TimeUnit.SECONDS);

        executor.shutdown();

        // Exactly one worker must succeed, and the other must fail
        assertThat(result1 ^ result2).isTrue();
        int successCount = (result1 ? 1 : 0) + (result2 ? 1 : 0);
        assertThat(successCount).isEqualTo(1);
    }
}
