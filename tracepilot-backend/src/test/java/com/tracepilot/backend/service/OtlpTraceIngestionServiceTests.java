package com.tracepilot.backend.service;

import com.google.protobuf.ByteString;
import com.tracepilot.backend.entity.SpanRecord;
import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.repository.SpanRecordRepository;
import com.tracepilot.backend.repository.TraceRecordRepository;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtlpTraceIngestionServiceTests {

    @Mock
    private SpanRecordRepository spanRecordRepository;

    @Mock
    private TraceRecordRepository traceRecordRepository;

    @Test
    void storesAllSpansAndProjectsOnlyServerSpansIntoAnalyticsRecords() {
        when(spanRecordRepository.existsByTraceIdAndSpanId(anyString(), anyString()))
                .thenReturn(false);
        when(traceRecordRepository.existsByTraceIdAndSpanId(anyString(), anyString()))
                .thenReturn(false);

        OtlpTraceIngestionService service = new OtlpTraceIngestionService(
                spanRecordRepository,
                traceRecordRepository,
                new ObjectMapper()
        );

        Span serverSpan = span(
                "00000000000000000000000000000001",
                "0000000000000001",
                Span.SpanKind.SPAN_KIND_SERVER,
                Status.StatusCode.STATUS_CODE_ERROR,
                1_000_000_000L,
                3_500_000_000L,
                500
        );
        Span clientSpan = span(
                "00000000000000000000000000000001",
                "0000000000000002",
                Span.SpanKind.SPAN_KIND_CLIENT,
                Status.StatusCode.STATUS_CODE_UNSET,
                1_200_000_000L,
                1_700_000_000L,
                200
        );

        Resource resource = Resource.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("service.name")
                        .setValue(AnyValue.newBuilder().setStringValue("order-service")))
                .build();
        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(resource)
                        .addScopeSpans(ScopeSpans.newBuilder()
                                .addSpans(serverSpan)
                                .addSpans(clientSpan)))
                .build();

        int ingested = service.ingest(request);

        assertThat(ingested).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SpanRecord>> spanCaptor = ArgumentCaptor.forClass(List.class);
        verify(spanRecordRepository).saveAll(spanCaptor.capture());
        assertThat(spanCaptor.getValue()).hasSize(2);
        assertThat(spanCaptor.getValue().getFirst().getServiceName()).isEqualTo("order-service");
        assertThat(spanCaptor.getValue().getFirst().getDurationMs()).isEqualTo(2500.0);
        assertThat(spanCaptor.getValue().getFirst().getHttpStatusCode()).isEqualTo(500);
        assertThat(spanCaptor.getValue().getFirst().getAttributesJson())
                .contains("service.name", "http.response.status_code");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TraceRecord>> traceCaptor = ArgumentCaptor.forClass(List.class);
        verify(traceRecordRepository).saveAll(traceCaptor.capture());
        assertThat(traceCaptor.getValue()).singleElement().satisfies(trace -> {
            assertThat(trace.getSpanId()).isEqualTo("0000000000000001");
            assertThat(trace.getParentSpanId()).isNull();
            assertThat(trace.getStatus()).isEqualTo("FAILED");
            assertThat(trace.getDurationMs()).isEqualTo(2500L);
        });
    }

    @Test
    void ignoresHealthCheckSpans() {
        OtlpTraceIngestionService service = new OtlpTraceIngestionService(
                spanRecordRepository,
                traceRecordRepository,
                new ObjectMapper()
        );
        Span healthSpan = span(
                "00000000000000000000000000000002",
                "0000000000000003",
                Span.SpanKind.SPAN_KIND_SERVER,
                Status.StatusCode.STATUS_CODE_OK,
                1_000_000_000L,
                1_010_000_000L,
                200
        ).toBuilder().setName("GET /actuator/health").build();
        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .addScopeSpans(ScopeSpans.newBuilder().addSpans(healthSpan)))
                .build();

        int ingested = service.ingest(request);

        assertThat(ingested).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SpanRecord>> spanCaptor = ArgumentCaptor.forClass(List.class);
        verify(spanRecordRepository).saveAll(spanCaptor.capture());
        assertThat(spanCaptor.getValue()).isEmpty();
    }

    private static Span span(
            String traceId,
            String spanId,
            Span.SpanKind kind,
            Status.StatusCode statusCode,
            long startNanos,
            long endNanos,
            int httpStatusCode) {
        return Span.newBuilder()
                .setTraceId(bytes(traceId))
                .setSpanId(bytes(spanId))
                .setName("GET /order/test")
                .setKind(kind)
                .setStartTimeUnixNano(startNanos)
                .setEndTimeUnixNano(endNanos)
                .setStatus(Status.newBuilder().setCode(statusCode))
                .addAttributes(KeyValue.newBuilder()
                        .setKey("http.response.status_code")
                        .setValue(AnyValue.newBuilder().setIntValue(httpStatusCode)))
                .build();
    }

    private static ByteString bytes(String hex) {
        return ByteString.copyFrom(HexFormat.of().parseHex(hex));
    }
}
