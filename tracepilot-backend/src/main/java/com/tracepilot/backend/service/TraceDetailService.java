package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.SpanView;
import com.tracepilot.backend.dto.TraceDetailResponse;
import com.tracepilot.backend.entity.SpanRecord;
import com.tracepilot.backend.exception.TraceIdNotFoundException;
import com.tracepilot.backend.repository.SpanRecordRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class TraceDetailService {

    private final SpanRecordRepository spanRecordRepository;
    private final ObjectMapper objectMapper;

    public TraceDetailService(
            SpanRecordRepository spanRecordRepository,
            ObjectMapper objectMapper) {
        this.spanRecordRepository = spanRecordRepository;
        this.objectMapper = objectMapper;
    }

    public TraceDetailResponse getTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }

        List<SpanRecord> records = spanRecordRepository
                .findByTraceIdOrderByStartTimeAsc(traceId.trim());
        if (records.isEmpty()) {
            throw new TraceIdNotFoundException(traceId);
        }

        Instant start = records.stream()
                .map(SpanRecord::getStartTime)
                .min(Instant::compareTo)
                .orElseThrow();
        Instant end = records.stream()
                .map(SpanRecord::getEndTime)
                .max(Instant::compareTo)
                .orElseThrow();
        boolean failed = records.stream().anyMatch(this::isFailed);
        List<String> services = records.stream()
                .map(SpanRecord::getServiceName)
                .distinct()
                .sorted()
                .toList();
        List<SpanView> spans = records.stream()
                .map(this::toView)
                .toList();

        double durationMs = Math.max(0.0, Duration.between(start, end).toNanos() / 1_000_000.0);
        return new TraceDetailResponse(
                traceId,
                start,
                end,
                durationMs,
                failed ? "FAILED" : "SUCCESS",
                spans.size(),
                services,
                spans
        );
    }

    private boolean isFailed(SpanRecord span) {
        return "ERROR".equalsIgnoreCase(span.getStatusCode())
                || (span.getHttpStatusCode() != null && span.getHttpStatusCode() >= 500);
    }

    private SpanView toView(SpanRecord span) {
        return new SpanView(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getServiceName(),
                span.getOperationName(),
                span.getSpanKind(),
                span.getStartTime(),
                span.getEndTime(),
                span.getDurationMs(),
                span.getStatusCode(),
                span.getHttpStatusCode(),
                parseAttributes(span.getAttributesJson())
        );
    }

    private java.util.Map<String, Object> parseAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
