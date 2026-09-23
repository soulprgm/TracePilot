package com.tracepilot.backend.controller;

import com.tracepilot.backend.dto.CreateTraceRequest;
import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.dto.TraceDetailResponse;
import com.tracepilot.backend.service.TraceDetailService;
import com.tracepilot.backend.service.TraceRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;

import java.util.List;


@RestController
@RequestMapping("/api/traces")
public class TraceRecordController {

    private final TraceRecordService traceRecordService;
    private final TraceDetailService traceDetailService;

    public TraceRecordController(
            TraceRecordService traceRecordService,
            TraceDetailService traceDetailService) {
        this.traceRecordService = traceRecordService;
        this.traceDetailService = traceDetailService;
    }
    @GetMapping("/{id}")
    public TraceRecord getTraceById(@PathVariable Long id) {
        return traceRecordService.getTraceById(id);
    }
    @PostMapping
    public TraceRecord createTrace(
            @Valid @RequestBody CreateTraceRequest request) {

        return traceRecordService.createTrace(request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrace(@PathVariable Long id) {

        traceRecordService.deleteTrace(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Page<TraceRecord> getTraces(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) Long minDurationMs,
            @RequestParam(required = false) Integer hours) {

        return traceRecordService.getTraces(
                page,
                size,
                serviceName,
                status,
                query,
                minDurationMs,
                hours
        );
    }

    @GetMapping("/services")
    public List<String> getServiceNames() {
        return traceRecordService.getServiceNames();
    }

    @GetMapping("/trace/{traceId}")
    public TraceDetailResponse getTraceDetail(@PathVariable String traceId) {
        return traceDetailService.getTrace(traceId);
    }
}
