package com.tracepilot.backend.repository;

import com.tracepilot.backend.entity.SpanRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpanRecordRepository extends JpaRepository<SpanRecord, Long> {

    boolean existsByTraceIdAndSpanId(String traceId, String spanId);

    List<SpanRecord> findByTraceIdOrderByStartTimeAsc(String traceId);

    Optional<SpanRecord> findFirstByTraceIdOrderByStartTimeAsc(String traceId);
}
