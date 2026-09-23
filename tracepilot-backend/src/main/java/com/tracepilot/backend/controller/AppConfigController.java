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

    public AppConfigController(
            @Value("${tracepilot.jaeger-url:http://localhost:16686}") String jaegerUrl) {
        this.jaegerUrl = jaegerUrl;
    }

    @GetMapping
    public Map<String, String> getConfig() {
        return Map.of(
                "application", "TracePilot",
                "jaegerUrl", jaegerUrl
        );
    }
}
