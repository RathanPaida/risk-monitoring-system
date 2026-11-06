package com.rathan.riskmonitor.transaction;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    public Device() {
    }

    public Device(Long id, Customer customer, String deviceHash, Instant firstSeenAt) {
        this.id = id;
        this.customer = customer;
        this.deviceHash = deviceHash;
        this.firstSeenAt = firstSeenAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getDeviceHash() {
        return deviceHash;
    }

    public void setDeviceHash(String deviceHash) {
        this.deviceHash = deviceHash;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }
}
