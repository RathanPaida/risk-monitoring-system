package com.rathan.riskmonitor.aws.reports;

import com.rathan.riskmonitor.aws.config.AwsProperties;
import com.rathan.riskmonitor.aws.reports.dto.ReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    public ReportService(S3Client s3Client, AwsProperties awsProperties) {
        this.s3Client = s3Client;
        this.awsProperties = awsProperties;
    }

    public List<ReportDto> listAvailableReports() {
        List<ReportDto> reports = new ArrayList<>();

        if (awsProperties.getS3().isEnabled()) {
            try {
                String bucket = awsProperties.getS3().getBucketName();
                reports.addAll(fetchS3Prefix(bucket, "reports/", "REPORT"));
                reports.addAll(fetchS3Prefix(bucket, "models/", "MODEL"));
            } catch (Exception e) {
                log.warn("Could not query reports from S3: {}", e.getMessage());
            }
        }

        // If S3 returned no items, check local ml directory as fallback
        if (reports.isEmpty()) {
            reports.addAll(scanLocalReports());
        }

        return reports;
    }

    private List<ReportDto> fetchS3Prefix(String bucket, String prefix, String category) {
        List<ReportDto> list = new ArrayList<>();
        try {
            ListObjectsV2Response resp = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build());

            if (resp.contents() != null) {
                for (S3Object obj : resp.contents()) {
                    if (obj.key().endsWith("/")) {
                        continue;
                    }
                    String name = obj.key().contains("/")
                            ? obj.key().substring(obj.key().lastIndexOf('/') + 1)
                            : obj.key();

                    list.add(new ReportDto(
                            name,
                            obj.key(),
                            category,
                            obj.size(),
                            obj.lastModified(),
                            "S3"
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("Error listing S3 objects under {}: {}", prefix, e.getMessage());
        }
        return list;
    }

    private List<ReportDto> scanLocalReports() {
        List<ReportDto> list = new ArrayList<>();
        File mlReportsDir = new File("ml/reports");
        if (!mlReportsDir.exists()) {
            mlReportsDir = new File("../ml/reports");
        }

        if (mlReportsDir.exists() && mlReportsDir.isDirectory()) {
            File[] files = mlReportsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        list.add(new ReportDto(
                                f.getName(),
                                "ml/reports/" + f.getName(),
                                "REPORT",
                                f.length(),
                                Instant.ofEpochMilli(f.lastModified()),
                                "LOCAL"
                        ));
                    }
                }
            }
        }

        File mlModel = new File("ml/isolation_forest.joblib");
        if (!mlModel.exists()) {
            mlModel = new File("../ml/isolation_forest.joblib");
        }

        if (mlModel.exists() && mlModel.isFile()) {
            list.add(new ReportDto(
                    mlModel.getName(),
                    "ml/" + mlModel.getName(),
                    "MODEL",
                    mlModel.length(),
                    Instant.ofEpochMilli(mlModel.lastModified()),
                    "LOCAL"
            ));
        }

        return list;
    }
}
