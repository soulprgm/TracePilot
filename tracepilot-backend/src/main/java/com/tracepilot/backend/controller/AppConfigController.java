package com.tracepilot.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class AppConfigController {

    private final String jaegerUrl;
    private final int retentionDays;

    public AppConfigController(
            @Value("${tracepilot.jaeger-url:http://localhost:16686}") String jaegerUrl,
            @Value("${tracepilot.retention-days:30}") int retentionDays) {
        this.jaegerUrl = jaegerUrl;
        this.retentionDays = retentionDays;
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of(
                "application", "TracePilot",
                "jaegerUrl", jaegerUrl,
                "retentionDays", retentionDays
        );
    }
}
