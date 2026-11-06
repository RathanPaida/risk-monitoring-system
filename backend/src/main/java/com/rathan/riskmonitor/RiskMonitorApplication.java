package com.rathan.riskmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RiskMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskMonitorApplication.class, args);
    }
}
