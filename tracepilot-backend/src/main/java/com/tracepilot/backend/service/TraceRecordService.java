package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.CreateTraceRequest;
import com.tracepilot.backend.dto.ServiceAnalyticsSummary;
import com.tracepilot.backend.dto.TraceAnalyticsSummary;
import com.tracepilot.backend.entity.TraceRecord;
import com.tracepilot.backend.exception.TraceNotFoundException;
import com.tracepilot.backend.repository.TraceRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TraceRecordService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_WINDOW_HOURS = 24 * 365;

    private final TraceRecordRepository traceRecordRepository;

    public TraceRecordService(TraceRecordRepository traceRecordRepository) {
        this.traceRecordRepository = traceRecordRepository;
    }

    public TraceRecord createTrace(CreateTraceRequest request) {
        TraceRecord traceRecord = new TraceRecord();
        traceRecord.setTraceId(request.getTraceId().trim());
        traceRecord.setServiceName(request.getServiceName().trim());
        traceRecord.setOperationName(request.getOperationName().trim());
        traceRecord.setStatus(request.getStatus().trim().toUpperCase(Locale.ROOT));
        traceRecord.setDurationMs(request.getDurationMs());
        return traceRecordRepository.save(traceRecord);
    }

    public TraceRecord getTraceById(Long id) {
        return traceRecordRepository.findById(id)
                .orElseThrow(() -> new TraceNotFoundException(id));
    }

    public void deleteTrace(Long id) {
        traceRecordRepository.delete(getTraceById(id));
    }

    public Page<TraceRecord> getTraces(
            int page,
            int size,
            String serviceName,
            String status,
            String queryText,
            Long minDurationMs,
            Integer hours) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        Specification<TraceRecord> specification = (root, query, builder) -> builder.conjunction();
        if (hasText(serviceName)) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("serviceName")), serviceName.trim().toLowerCase(Locale.ROOT)));
        }
        if (hasText(status)) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.upper(root.get("status")), status.trim().toUpperCase(Locale.ROOT)));
        }
        if (hasText(queryText)) {
            String pattern = "%" + queryText.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("traceId")), pattern),
                    builder.like(builder.lower(root.get("operationName")), pattern)
            ));
        }
        if (minDurationMs != null && minDurationMs >= 0) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("durationMs"), minDurationMs));
        }
        LocalDateTime since = windowStart(hours);
        if (since != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), since));
        }

        return traceRecordRepository.findAll(specification, pageable);
    }

    public List<String> getServiceNames() {
        return traceRecordRepository.findDistinctServiceNames();
    }

    public List<TraceRecord> getSlowTraces(Long thresholdMs) {
        if (thresholdMs == null || thresholdMs < 0) {
            throw new IllegalArgumentException("thresholdMs must be zero or greater");
        }
        return traceRecordRepository.findByDurationMsGreaterThanEqual(thresholdMs);
    }

    public List<TraceRecord> getFailedTraces() {
        return traceRecordRepository.findByStatusIgnoreCase("FAILED");
    }

    public TraceAnalyticsSummary getAnalyticsSummary(Long slowThresholdMs, Integer hours) {
        if (slowThresholdMs == null || slowThresholdMs < 0) {
            throw new IllegalArgumentException("slowThresholdMs must be zero or greater");
        }
        LocalDateTime since = windowStart(hours);
        long totalTraces = since == null
                ? traceRecordRepository.countDistinctTraceIds()
                : traceRecordRepository.countDistinctTraceIdsSince(since);
        long failedTraces = since == null
                ? traceRecordRepository.countDistinctFailedTraceIds()
                : traceRecordRepository.countDistinctFailedTraceIdsSince(since);
        long slowTraces = since == null
                ? traceRecordRepository.countDistinctSlowTraceIds(slowThresholdMs)
                : traceRecordRepository.countDistinctSlowTraceIdsSince(slowThresholdMs, since);
        double errorRate = totalTraces == 0 ? 0.0 : ((double) failedTraces / totalTraces) * 100;
        Double averageLatency = since == null
                ? traceRecordRepository.averageRootTraceDuration()
                : traceRecordRepository.averageRootTraceDurationSince(since);

        return new TraceAnalyticsSummary(
                totalTraces,
                failedTraces,
                errorRate,
                averageLatency == null ? 0.0 : averageLatency,
                slowTraces
        );
    }

    public List<ServiceAnalyticsSummary> getServiceAnalytics(Integer hours) {
        LocalDateTime since = windowStart(hours);
        List<TraceRecord> allTraces = since == null
                ? traceRecordRepository.findAll()
                : traceRecordRepository.findByCreatedAtGreaterThanEqual(since);
        Map<String, List<TraceRecord>> grouped = allTraces.stream()
                .collect(Collectors.groupingBy(TraceRecord::getServiceName));
        List<ServiceAnalyticsSummary> result = new ArrayList<>();

        for (Map.Entry<String, List<TraceRecord>> entry : grouped.entrySet()) {
            List<TraceRecord> traces = entry.getValue();
            long requestCount = traces.size();
            long failedCount = traces.stream()
                    .filter(trace -> "FAILED".equalsIgnoreCase(trace.getStatus()))
                    .count();
            double errorRate = requestCount == 0 ? 0.0 : ((double) failedCount / requestCount) * 100;
            double averageLatencyMs = traces.stream()
                    .mapToLong(TraceRecord::getDurationMs)
                    .average()
                    .orElse(0.0);
            List<Long> latencies = traces.stream()
                    .map(TraceRecord::getDurationMs)
                    .sorted()
                    .toList();

            result.add(new ServiceAnalyticsSummary(
                    entry.getKey(),
                    requestCount,
                    failedCount,
                    errorRate,
                    averageLatencyMs,
                    calculatePercentile(latencies, 0.95),
                    calculatePercentile(latencies, 0.99)
            ));
        }

        return result.stream()
                .sorted((left, right) -> left.getServiceName().compareToIgnoreCase(right.getServiceName()))
                .toList();
    }

    private LocalDateTime windowStart(Integer hours) {
        if (hours == null) {
            return null;
        }
        if (hours <= 0 || hours > MAX_WINDOW_HOURS) {
            throw new IllegalArgumentException("hours must be between 1 and " + MAX_WINDOW_HOURS);
        }
        return LocalDateTime.now().minusHours(hours);
    }

    private double calculatePercentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
