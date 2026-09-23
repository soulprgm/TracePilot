package com.tracepilot.backend.dto;

import java.time.Instant;

public record DemoScenarioResponse(
        String scenario,
        int upstreamStatus,
        boolean expectedOutcome,
        String message,
        Instant triggeredAt) {
}
