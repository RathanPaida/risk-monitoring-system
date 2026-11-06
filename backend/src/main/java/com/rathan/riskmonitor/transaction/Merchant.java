package com.rathan.riskmonitor.transaction;

import jakarta.persistence.*;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 10)
    private String country;

    public Merchant() {
    }

    public Merchant(Long id, String name, String category, String country) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.country = country;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
