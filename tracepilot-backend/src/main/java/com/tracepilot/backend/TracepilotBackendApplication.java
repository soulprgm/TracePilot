package com.tracepilot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TracepilotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TracepilotBackendApplication.class, args);
    }

}
