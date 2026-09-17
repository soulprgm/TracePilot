package com.tracepilot.backend.controller;

import com.tracepilot.backend.entity.SpanRecord;
import com.tracepilot.backend.repository.SpanRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spans")
public class SpanRecordController {

    private final SpanRecordRepository spanRecordRepository;

    public SpanRecordController(SpanRecordRepository spanRecordRepository) {
        this.spanRecordRepository = spanRecordRepository;
    }

    @GetMapping
    public List<SpanRecord> getSpansByTraceId(@RequestParam String traceId) {
        return spanRecordRepository.findByTraceIdOrderByStartTimeAsc(traceId);
    }
}
