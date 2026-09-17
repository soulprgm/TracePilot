package com.tracepilot.backend.controller;

import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.service.TraceRecordService;
import org.springframework.web.bind.annotation.*;
import com.tracepilot.backend.dto.TraceAnalyticsSummary;
import com.tracepilot.backend.dto.ServiceAnalyticsSummary;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class TraceAnalyticsController {

    private final TraceRecordService traceRecordService;

    public TraceAnalyticsController(
            TraceRecordService traceRecordService) {
        this.traceRecordService = traceRecordService;
    }

    @GetMapping("/slow-traces")
    public List<TraceRecord> getSlowTraces(
            @RequestParam(defaultValue = "1000")
            Long thresholdMs) {

        return traceRecordService
                .getSlowTraces(thresholdMs);
    }

    @GetMapping("/failed-traces")
    public List<TraceRecord> getFailedTraces() {

        return traceRecordService
                .getFailedTraces();
    }
    @GetMapping("/summary")
    public TraceAnalyticsSummary getSummary(
            @RequestParam(defaultValue = "1000")
            Long slowThresholdMs) {

        return traceRecordService
                .getAnalyticsSummary(slowThresholdMs);
    }
    @GetMapping("/services")
    public List<ServiceAnalyticsSummary> getServiceAnalytics() {

        return traceRecordService
                .getServiceAnalytics();
    }
}