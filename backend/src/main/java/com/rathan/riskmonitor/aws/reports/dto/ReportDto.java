package com.rathan.riskmonitor.aws.reports.dto;

import java.time.Instant;

public class ReportDto {

    private String name;
    private String key;
    private String category;
    private Long sizeBytes;
    private Instant lastModified;
    private String storageType;

    public ReportDto() {
    }

    public ReportDto(String name, String key, String category, Long sizeBytes, Instant lastModified, String storageType) {
        this.name = name;
        this.key = key;
        this.category = category;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
        this.storageType = storageType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }
}

