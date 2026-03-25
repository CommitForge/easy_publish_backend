package com.easypublish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

@SpringBootApplication
@EnableScheduling
public class EasyPublishApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyPublishApplication.class, args);
    }

}