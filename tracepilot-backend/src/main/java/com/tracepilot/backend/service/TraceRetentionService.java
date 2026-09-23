package com.tracepilot.backend.service;

import com.tracepilot.backend.repository.SpanRecordRepository;
import com.tracepilot.backend.repository.TraceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TraceRetentionService {

    private static final Logger log = LoggerFactory.getLogger(TraceRetentionService.class);
    private static final String HEALTH_OPERATION = "GET /actuator/health";

    private final SpanRecordRepository spanRecordRepository;
    private final TraceRecordRepository traceRecordRepository;
    private final int retentionDays;

    public TraceRetentionService(
            SpanRecordRepository spanRecordRepository,
            TraceRecordRepository traceRecordRepository,
            @Value("${tracepilot.retention-days:30}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("tracepilot.retention-days must be at least 1");
        }
        this.spanRecordRepository = spanRecordRepository;
        this.traceRecordRepository = traceRecordRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${tracepilot.retention-cron:0 15 3 * * *}", zone = "UTC")
    @Transactional
    public void deleteExpiredTelemetry() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86_400L);
        long spans = spanRecordRepository.deleteByIngestedAtBefore(cutoff);
        long traces = traceRecordRepository.deleteByCreatedAtBefore(
                LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC)
        );
        if (spans > 0 || traces > 0) {
            log.info("Deleted expired telemetry: {} spans and {} trace records", spans, traces);
        }
    }

    @Scheduled(
            initialDelayString = "${tracepilot.noise-cleanup-initial-delay-ms:10000}",
            fixedDelayString = "${tracepilot.noise-cleanup-delay-ms:3600000}"
    )
    @Transactional
    public void deleteHealthCheckTelemetry() {
        long spans = spanRecordRepository.deleteByOperationName(HEALTH_OPERATION);
        long traces = traceRecordRepository.deleteByOperationName(HEALTH_OPERATION);
        if (spans > 0 || traces > 0) {
            log.info("Deleted health-check telemetry noise: {} spans and {} trace records", spans, traces);
        }
    }
}
