package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.TraceDetailResponse;
import com.tracepilot.backend.entity.SpanRecord;
import com.tracepilot.backend.repository.SpanRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceDetailServiceTests {

    @Mock
    private SpanRecordRepository spanRecordRepository;

    @Test
    void assemblesOrderedWaterfallAndPropagatesFailureStatus() {
        SpanRecord root = span(
                "root", null, "order-service", "GET /order/fail",
                "SERVER", "UNSET", 500,
                "2026-09-22T12:00:00Z", "2026-09-22T12:00:02.500Z",
                "{\"http.route\":\"/order/fail\"}"
        );
        SpanRecord child = span(
                "child", "root", "payment-service", "GET /payment/fail",
                "SERVER", "ERROR", 500,
                "2026-09-22T12:00:00.300Z", "2026-09-22T12:00:02.300Z",
                "{\"exception.type\":\"RuntimeException\"}"
        );
        when(spanRecordRepository.findByTraceIdOrderByStartTimeAsc("trace-1"))
                .thenReturn(List.of(root, child));

        TraceDetailResponse result = new TraceDetailService(
                spanRecordRepository,
                new ObjectMapper()
        ).getTrace("trace-1");

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.durationMs()).isEqualTo(2500.0);
        assertThat(result.spanCount()).isEqualTo(2);
        assertThat(result.services()).containsExactly("order-service", "payment-service");
        assertThat(result.spans().getFirst().attributes()).containsEntry("http.route", "/order/fail");
    }

    private static SpanRecord span(
            String spanId,
            String parentSpanId,
            String service,
            String operation,
            String kind,
            String status,
            Integer httpStatus,
            String start,
            String end,
            String attributes) {
        SpanRecord span = new SpanRecord();
        span.setTraceId("trace-1");
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setServiceName(service);
        span.setOperationName(operation);
        span.setSpanKind(kind);
        span.setStatusCode(status);
        span.setHttpStatusCode(httpStatus);
        span.setStartTime(Instant.parse(start));
        span.setEndTime(Instant.parse(end));
        span.setDurationMs((double) java.time.Duration.between(span.getStartTime(), span.getEndTime()).toMillis());
        span.setAttributesJson(attributes);
        return span;
    }
}
