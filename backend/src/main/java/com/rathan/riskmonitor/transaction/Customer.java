package com.rathan.riskmonitor.transaction;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "home_country", nullable = false, length = 10)
    private String homeCountry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Customer() {
    }

    public Customer(Long id, String name, String homeCountry, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.homeCountry = homeCountry;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHomeCountry() {
        return homeCountry;
    }

    public void setHomeCountry(String homeCountry) {
        this.homeCountry = homeCountry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
