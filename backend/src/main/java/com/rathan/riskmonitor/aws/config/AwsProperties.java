package com.rathan.riskmonitor.aws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "us-east-1";
    private String endpoint = "";
    private String accessKeyId = "test";
    private String secretAccessKey = "test";
    private S3 s3 = new S3();
    private DynamoDb dynamodb = new DynamoDb();

    public static class S3 {
        private boolean enabled = true;
        private String bucketName = "risk-monitoring-bucket";
        private String incomingPrefix = "incoming/";
        private String processedPrefix = "processed/";
        private String failedPrefix = "failed/";
        private long pollRateMs = 60000;
        private int workerThreads = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getIncomingPrefix() {
            return incomingPrefix;
        }

        public void setIncomingPrefix(String incomingPrefix) {
            this.incomingPrefix = incomingPrefix;
        }

        public String getProcessedPrefix() {
            return processedPrefix;
        }

        public void setProcessedPrefix(String processedPrefix) {
            this.processedPrefix = processedPrefix;
        }

        public String getFailedPrefix() {
            return failedPrefix;
        }

        public void setFailedPrefix(String failedPrefix) {
            this.failedPrefix = failedPrefix;
        }

        public long getPollRateMs() {
            return pollRateMs;
        }

        public void setPollRateMs(long pollRateMs) {
            this.pollRateMs = pollRateMs;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }
    }

    public static class DynamoDb {
        private boolean enabled = true;
        private String tableName = "ingestion_jobs";
        private int leaseDurationSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public int getLeaseDurationSeconds() {
            return leaseDurationSeconds;
        }

        public void setLeaseDurationSeconds(int leaseDurationSeconds) {
            this.leaseDurationSeconds = leaseDurationSeconds;
        }
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    public DynamoDb getDynamodb() {
        return dynamodb;
    }

    public void setDynamodb(DynamoDb dynamodb) {
        this.dynamodb = dynamodb;
    }
}
