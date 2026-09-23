package com.tracepilot.backend.dto;

import java.time.Instant;
import java.util.Map;

public record SpanView(
        String spanId,
        String parentSpanId,
        String serviceName,
        String operationName,
        String spanKind,
        Instant startTime,
        Instant endTime,
        double durationMs,
        String statusCode,
        Integer httpStatusCode,
        Map<String, Object> attributes) {
}
