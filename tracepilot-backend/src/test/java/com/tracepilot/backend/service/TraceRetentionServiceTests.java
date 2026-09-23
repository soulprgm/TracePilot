package com.tracepilot.backend.service;

import com.tracepilot.backend.repository.SpanRecordRepository;
import com.tracepilot.backend.repository.TraceRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceRetentionServiceTests {

    @Test
    void removesExpiredSpanAndAnalyticsRecords() {
        SpanRecordRepository spans = mock(SpanRecordRepository.class);
        TraceRecordRepository traces = mock(TraceRecordRepository.class);
        TraceRetentionService service = new TraceRetentionService(spans, traces, 30);

        service.deleteExpiredTelemetry();

        verify(spans).deleteByIngestedAtBefore(any(Instant.class));
        verify(traces).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void refusesInvalidRetentionPeriod() {
        assertThatThrownBy(() -> new TraceRetentionService(
                mock(SpanRecordRepository.class),
                mock(TraceRecordRepository.class),
                0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removesHealthCheckNoise() {
        SpanRecordRepository spans = mock(SpanRecordRepository.class);
        TraceRecordRepository traces = mock(TraceRecordRepository.class);
        TraceRetentionService service = new TraceRetentionService(spans, traces, 30);

        service.deleteHealthCheckTelemetry();

        verify(spans).deleteByOperationName("GET /actuator/health");
        verify(traces).deleteByOperationName("GET /actuator/health");
    }
}
