package com.rathan.riskmonitor.aws;

import com.rathan.riskmonitor.aws.config.AwsProperties;
import com.rathan.riskmonitor.aws.reports.ReportService;
import com.rathan.riskmonitor.aws.reports.dto.ReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private S3Client s3Client;

    private AwsProperties properties;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        properties = new AwsProperties();
        properties.getS3().setEnabled(true);
        properties.getS3().setBucketName("test-bucket");
        reportService = new ReportService(s3Client, properties);
    }

    @Test
    void testListAvailableReports_FromS3() {
        S3Object reportObj = S3Object.builder()
                .key("reports/evaluation_report.json")
                .size(1024L)
                .lastModified(Instant.now())
                .build();

        S3Object modelObj = S3Object.builder()
                .key("models/isolation_forest.joblib")
                .size(204800L)
                .lastModified(Instant.now())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenAnswer(inv -> {
                    ListObjectsV2Request req = inv.getArgument(0);
                    if ("reports/".equals(req.prefix())) {
                        return ListObjectsV2Response.builder().contents(List.of(reportObj)).build();
                    } else if ("models/".equals(req.prefix())) {
                        return ListObjectsV2Response.builder().contents(List.of(modelObj)).build();
                    }
                    return ListObjectsV2Response.builder().build();
                });

        List<ReportDto> reports = reportService.listAvailableReports();

        assertThat(reports).hasSize(2);
        assertThat(reports).extracting(ReportDto::getName)
                .containsExactlyInAnyOrder("evaluation_report.json", "isolation_forest.joblib");
        assertThat(reports).extracting(ReportDto::getStorageType)
                .containsOnly("S3");
    }

    @Test
    void testListAvailableReports_FallbackToLocal_WhenS3Empty() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());

        List<ReportDto> reports = reportService.listAvailableReports();

        // Local ml reports exist in the workspace, so scanLocalReports will find them
        assertThat(reports).isNotEmpty();
        assertThat(reports.get(0).getStorageType()).isEqualTo("LOCAL");
    }
}

