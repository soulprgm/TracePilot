package com.tracepilot.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "span_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_span_trace_span",
                columnNames = {"trace_id", "span_id"}
        ),
        indexes = {
                @Index(name = "idx_span_trace_id", columnList = "trace_id"),
                @Index(name = "idx_span_service_name", columnList = "service_name"),
                @Index(name = "idx_span_status_code", columnList = "status_code"),
                @Index(name = "idx_span_start_time", columnList = "start_time")
        }
)
public class SpanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 32)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 16)
    private String spanId;

    @Column(name = "parent_span_id", length = 16)
    private String parentSpanId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "operation_name", nullable = false)
    private String operationName;

    @Column(name = "span_kind", nullable = false, length = 16)
    private String spanKind;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "duration_ms", nullable = false)
    private Double durationMs;

    @Column(name = "status_code", nullable = false, length = 16)
    private String statusCode;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    private String attributesJson;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @PrePersist
    public void prePersist() {
        if (ingestedAt == null) {
            ingestedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getSpanKind() { return spanKind; }
    public void setSpanKind(String spanKind) { this.spanKind = spanKind; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public Double getDurationMs() { return durationMs; }
    public void setDurationMs(Double durationMs) { this.durationMs = durationMs; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Integer getHttpStatusCode() { return httpStatusCode; }
    public void setHttpStatusCode(Integer httpStatusCode) { this.httpStatusCode = httpStatusCode; }
    public String getAttributesJson() { return attributesJson; }
    public void setAttributesJson(String attributesJson) { this.attributesJson = attributesJson; }
    public Instant getIngestedAt() { return ingestedAt; }
}
