package com.tracepilot.backend.dto;

import java.time.Instant;
import java.util.List;

public record TraceAiAnalysisResponse(
        String traceId,
        String analysisMode,
        String model,
        String severity,
        String summary,
        String probableRootCause,
        List<String> evidence,
        List<String> recommendations,
        Instant generatedAt) {
}
