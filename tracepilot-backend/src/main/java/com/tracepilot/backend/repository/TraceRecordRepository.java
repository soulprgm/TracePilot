package com.tracepilot.backend.repository;

import com.tracepilot.backend.entity.TraceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface TraceRecordRepository
        extends JpaRepository<TraceRecord, Long>, JpaSpecificationExecutor<TraceRecord> {

    Page<TraceRecord> findByServiceName(
            String serviceName,
            Pageable pageable
    );

    Page<TraceRecord> findByStatus(
            String status,
            Pageable pageable
    );

    List<TraceRecord> findByDurationMsGreaterThanEqual(
            Long durationMs
    );

    List<TraceRecord> findByStatusIgnoreCase(
            String status
    );

    long countByStatusIgnoreCase(
            String status
    );

    long countByDurationMsGreaterThanEqual(
            Long durationMs
    );
    List<TraceRecord> findByServiceName(String serviceName);

    List<TraceRecord> findByCreatedAtGreaterThanEqual(LocalDateTime since);

    @Query("select distinct trace.serviceName from TraceRecord trace order by trace.serviceName")
    List<String> findDistinctServiceNames();

    boolean existsByTraceIdAndSpanId(String traceId, String spanId);

    @Query("select count(distinct trace.traceId) from TraceRecord trace")
    long countDistinctTraceIds();

    @Query("""
            select count(distinct trace.traceId)
            from TraceRecord trace
            where upper(trace.status) = 'FAILED'
            """)
    long countDistinctFailedTraceIds();

    @Query("""
            select count(distinct trace.traceId)
            from TraceRecord trace
            where trace.durationMs >= :thresholdMs
            """)
    long countDistinctSlowTraceIds(@Param("thresholdMs") Long thresholdMs);

    @Query("""
            select avg(trace.durationMs)
            from TraceRecord trace
            where trace.parentSpanId is null
            """)
    Double averageRootTraceDuration();

    @Query("""
            select count(distinct trace.traceId)
            from TraceRecord trace
            where trace.createdAt >= :since
            """)
    long countDistinctTraceIdsSince(@Param("since") LocalDateTime since);

    @Query("""
            select count(distinct trace.traceId)
            from TraceRecord trace
            where upper(trace.status) = 'FAILED'
              and trace.createdAt >= :since
            """)
    long countDistinctFailedTraceIdsSince(@Param("since") LocalDateTime since);

    @Query("""
            select count(distinct trace.traceId)
            from TraceRecord trace
            where trace.durationMs >= :thresholdMs
              and trace.createdAt >= :since
            """)
    long countDistinctSlowTraceIdsSince(
            @Param("thresholdMs") Long thresholdMs,
            @Param("since") LocalDateTime since);

    @Query("""
            select avg(trace.durationMs)
            from TraceRecord trace
            where trace.parentSpanId is null
              and trace.createdAt >= :since
            """)
    Double averageRootTraceDurationSince(@Param("since") LocalDateTime since);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);

    long deleteByOperationName(String operationName);
}
