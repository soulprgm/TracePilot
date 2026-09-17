package com.tracepilot.backend.controller;

import com.tracepilot.backend.dto.CreateTraceRequest;
import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.service.TraceRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;


@RestController
@RequestMapping("/api/traces")
public class TraceRecordController {

    private final TraceRecordService traceRecordService;

    public TraceRecordController(TraceRecordService traceRecordService) {
        this.traceRecordService = traceRecordService;
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
            @RequestParam(required = false) String status) {

        return traceRecordService.getTraces(
                page,
                size,
                serviceName,
                status
        );
    }
}