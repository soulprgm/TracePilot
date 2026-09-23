package com.tracepilot.backend.dto;

import java.time.Instant;
import java.util.List;

public record TraceDetailResponse(
        String traceId,
        Instant startTime,
        Instant endTime,
        double durationMs,
        String status,
        int spanCount,
        List<String> services,
        List<SpanView> spans) {
}
