package com.candleservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // enables @Scheduled for simulator and flusher
public class CandleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandleServiceApplication.class, args);
    }
}