package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.ServiceAnalyticsSummary;
import com.tracepilot.backend.dto.TraceAnalyticsSummary;
import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.repository.TraceRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceRecordServiceTests {

    @Mock
    private TraceRecordRepository repository;

    @Test
    void calculatesDistinctTraceSummary() {
        when(repository.countDistinctTraceIds()).thenReturn(20L);
        when(repository.countDistinctFailedTraceIds()).thenReturn(3L);
        when(repository.countDistinctSlowTraceIds(1000L)).thenReturn(5L);
        when(repository.averageRootTraceDuration()).thenReturn(425.5);

        TraceAnalyticsSummary summary = new TraceRecordService(repository)
                .getAnalyticsSummary(1000L, null);

        assertThat(summary.getTotalTraces()).isEqualTo(20);
        assertThat(summary.getFailedTraces()).isEqualTo(3);
        assertThat(summary.getErrorRate()).isEqualTo(15.0);
        assertThat(summary.getAverageLatencyMs()).isEqualTo(425.5);
        assertThat(summary.getSlowTraces()).isEqualTo(5);
    }

    @Test
    void calculatesServiceTailLatencyAndFailureRate() {
        when(repository.findAll()).thenReturn(List.of(
                trace("payment-service", "SUCCESS", 100),
                trace("payment-service", "SUCCESS", 200),
                trace("payment-service", "FAILED", 2500)
        ));

        List<ServiceAnalyticsSummary> result = new TraceRecordService(repository)
                .getServiceAnalytics(null);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.getRequestCount()).isEqualTo(3);
            assertThat(summary.getFailedCount()).isEqualTo(1);
            assertThat(summary.getErrorRate()).isCloseTo(33.333, org.assertj.core.data.Offset.offset(0.01));
            assertThat(summary.getAverageLatencyMs()).isCloseTo(933.333, org.assertj.core.data.Offset.offset(0.01));
            assertThat(summary.getP95LatencyMs()).isEqualTo(2500.0);
            assertThat(summary.getP99LatencyMs()).isEqualTo(2500.0);
        });
    }

    private static TraceRecord trace(String service, String status, long durationMs) {
        TraceRecord record = new TraceRecord();
        record.setTraceId(service + "-" + durationMs);
        record.setServiceName(service);
        record.setOperationName("GET /payment");
        record.setStatus(status);
        record.setDurationMs(durationMs);
        return record;
    }
}
