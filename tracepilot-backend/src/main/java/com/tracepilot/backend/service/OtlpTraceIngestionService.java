package com.tracepilot.backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import com.tracepilot.backend.entity.SpanRecord;
import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.repository.SpanRecordRepository;
import com.tracepilot.backend.repository.TraceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class OtlpTraceIngestionService {

    private static final String UNKNOWN_SERVICE = "unknown-service";

    private final SpanRecordRepository spanRecordRepository;
    private final TraceRecordRepository traceRecordRepository;
    private final ObjectMapper objectMapper;

    public OtlpTraceIngestionService(
            SpanRecordRepository spanRecordRepository,
            TraceRecordRepository traceRecordRepository,
            ObjectMapper objectMapper) {
        this.spanRecordRepository = spanRecordRepository;
        this.traceRecordRepository = traceRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int ingest(ExportTraceServiceRequest request) {
        List<SpanRecord> newSpans = new ArrayList<>();
        List<TraceRecord> newTraceRecords = new ArrayList<>();
        Set<String> spansSeenInRequest = new HashSet<>();

        for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
            Map<String, Object> resourceAttributes = attributesToMap(
                    resourceSpans.hasResource()
                            ? resourceSpans.getResource()
                            : Resource.getDefaultInstance()
            );
            String serviceName = stringAttribute(resourceAttributes, "service.name", UNKNOWN_SERVICE);

            for (ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
                for (Span span : scopeSpans.getSpansList()) {
                    String traceId = hex(span.getTraceId());
                    String spanId = hex(span.getSpanId());
                    if (traceId.isBlank() || spanId.isBlank()) {
                        continue;
                    }

                    String spanKey = traceId + ":" + spanId;
                    if (!spansSeenInRequest.add(spanKey)
                            || spanRecordRepository.existsByTraceIdAndSpanId(traceId, spanId)) {
                        continue;
                    }

                    Map<String, Object> allAttributes = new LinkedHashMap<>(resourceAttributes);
                    allAttributes.putAll(attributesToMap(span.getAttributesList()));

                    SpanRecord spanRecord = toSpanRecord(span, traceId, spanId, serviceName, allAttributes);
                    newSpans.add(spanRecord);

                    if ("SERVER".equals(spanRecord.getSpanKind())
                            && !traceRecordRepository.existsByTraceIdAndSpanId(traceId, spanId)) {
                        newTraceRecords.add(toTraceRecord(spanRecord));
                    }
                }
            }
        }

        spanRecordRepository.saveAll(newSpans);
        traceRecordRepository.saveAll(newTraceRecords);
        return newSpans.size();
    }

    private SpanRecord toSpanRecord(
            Span span,
            String traceId,
            String spanId,
            String serviceName,
            Map<String, Object> attributes) {
        long startNanos = span.getStartTimeUnixNano();
        long endNanos = span.getEndTimeUnixNano();
        double durationMs = endNanos >= startNanos
                ? (endNanos - startNanos) / 1_000_000.0
                : 0.0;

        SpanRecord result = new SpanRecord();
        result.setTraceId(traceId);
        result.setSpanId(spanId);
        result.setParentSpanId(span.getParentSpanId().isEmpty() ? null : hex(span.getParentSpanId()));
        result.setServiceName(serviceName);
        result.setOperationName(span.getName().isBlank() ? "unnamed-span" : span.getName());
        result.setSpanKind(normalizeEnum(span.getKind().name(), "SPAN_KIND_"));
        result.setStartTime(toInstant(startNanos));
        result.setEndTime(toInstant(endNanos));
        result.setDurationMs(durationMs);
        result.setStatusCode(normalizeEnum(span.getStatus().getCode().name(), "STATUS_CODE_"));
        result.setHttpStatusCode(integerAttribute(attributes,
                "http.response.status_code", "http.status_code"));
        result.setAttributesJson(writeAttributes(attributes));
        return result;
    }

    private TraceRecord toTraceRecord(SpanRecord span) {
        boolean failed = "ERROR".equals(span.getStatusCode())
                || (span.getHttpStatusCode() != null && span.getHttpStatusCode() >= 500);

        TraceRecord result = new TraceRecord();
        result.setTraceId(span.getTraceId());
        result.setSpanId(span.getSpanId());
        result.setParentSpanId(span.getParentSpanId());
        result.setServiceName(span.getServiceName());
        result.setOperationName(span.getOperationName());
        result.setStatus(failed ? "FAILED" : "SUCCESS");
        result.setDurationMs(Math.round(span.getDurationMs()));
        return result;
    }

    private Map<String, Object> attributesToMap(Resource resource) {
        return attributesToMap(resource.getAttributesList());
    }

    private Map<String, Object> attributesToMap(List<KeyValue> attributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValue attribute : attributes) {
            result.put(attribute.getKey(), anyValue(attribute.getValue()));
        }
        return result;
    }

    private Object anyValue(AnyValue value) {
        return switch (value.getValueCase()) {
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case INT_VALUE -> value.getIntValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case BYTES_VALUE -> hex(value.getBytesValue());
            case ARRAY_VALUE -> value.getArrayValue().getValuesList().stream()
                    .map(this::anyValue)
                    .toList();
            case KVLIST_VALUE -> attributesToMap(value.getKvlistValue().getValuesList());
            case VALUE_NOT_SET -> null;
            default -> null;
        };
    }

    private String writeAttributes(Map<String, Object> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to serialize span attributes", exception);
        }
    }

    private static String stringAttribute(
            Map<String, Object> attributes,
            String key,
            String defaultValue) {
        Object value = attributes.get(key);
        return value instanceof String string && !string.isBlank() ? string : defaultValue;
    }

    private static Integer integerAttribute(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private static Instant toInstant(long unixNanos) {
        long seconds = Math.floorDiv(unixNanos, 1_000_000_000L);
        long nanos = Math.floorMod(unixNanos, 1_000_000_000L);
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static String normalizeEnum(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static String hex(ByteString bytes) {
        return HexFormat.of().formatHex(bytes.toByteArray());
    }
}
