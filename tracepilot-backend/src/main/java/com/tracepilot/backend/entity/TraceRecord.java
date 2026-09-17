package com.tracepilot.backend.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;


    @Entity
    @Table(
            name = "trace_records",
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_trace_record_trace_span",
                    columnNames = {"trace_id", "span_id"}
            )
    )
    public class TraceRecord {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "trace_id")
        private String traceId;

        @Column(name = "span_id", length = 16)
        private String spanId;

        @Column(name = "parent_span_id", length = 16)
        private String parentSpanId;

        private String serviceName;

        private String operationName;

        private String status;

        private Long durationMs;

        private LocalDateTime createdAt;

        public TraceRecord() {
        }

        @PrePersist
        public void prePersist() {
            createdAt = LocalDateTime.now();
        }

        public Long getId() {
            return id;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public void setSpanId(String spanId) {
            this.spanId = spanId;
        }

        public String getParentSpanId() {
            return parentSpanId;
        }

        public void setParentSpanId(String parentSpanId) {
            this.parentSpanId = parentSpanId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getOperationName() {
            return operationName;
        }

        public void setOperationName(String operationName) {
            this.operationName = operationName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
