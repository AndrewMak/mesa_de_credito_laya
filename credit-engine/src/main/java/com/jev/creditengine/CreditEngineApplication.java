package com.jev.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CreditEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditEngineApplication.class, args);
    }
}
