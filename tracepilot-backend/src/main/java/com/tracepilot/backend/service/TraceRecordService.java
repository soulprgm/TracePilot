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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TraceRecordService {

    private final TraceRecordRepository traceRecordRepository;

    public TraceRecordService(
            TraceRecordRepository traceRecordRepository) {
        this.traceRecordRepository = traceRecordRepository;
    }


    // =========================
    // 1. 创建 Trace
    // =========================

    public TraceRecord createTrace(
            CreateTraceRequest request) {

        TraceRecord traceRecord =
                new TraceRecord();

        traceRecord.setTraceId(
                request.getTraceId()
        );

        traceRecord.setServiceName(
                request.getServiceName()
        );

        traceRecord.setOperationName(
                request.getOperationName()
        );

        traceRecord.setStatus(
                request.getStatus()
        );

        traceRecord.setDurationMs(
                request.getDurationMs()
        );

        return traceRecordRepository
                .save(traceRecord);
    }


    // =========================
    // 2. 根据 ID 查询
    // =========================

    public TraceRecord getTraceById(
            Long id) {

        return traceRecordRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new TraceNotFoundException(id)
                );
    }


    // =========================
    // 3. 删除 Trace
    // =========================

    public void deleteTrace(
            Long id) {

        TraceRecord traceRecord =
                getTraceById(id);

        traceRecordRepository
                .delete(traceRecord);
    }


    // =========================
    // 4. 分页 + 条件查询
    // =========================

    public Page<TraceRecord> getTraces(
            int page,
            int size,
            String serviceName,
            String status) {

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by("createdAt")
                                .descending()
                );

        if (serviceName != null) {

            return traceRecordRepository
                    .findByServiceName(
                            serviceName,
                            pageable
                    );
        }

        if (status != null) {

            return traceRecordRepository
                    .findByStatus(
                            status,
                            pageable
                    );
        }

        return traceRecordRepository
                .findAll(pageable);
    }


    // =========================
    // 5. 查询 Slow Traces
    // =========================

    public List<TraceRecord> getSlowTraces(
            Long thresholdMs) {

        return traceRecordRepository
                .findByDurationMsGreaterThanEqual(
                        thresholdMs
                );
    }


    // =========================
    // 6. 查询 Failed Traces
    // =========================

    public List<TraceRecord> getFailedTraces() {

        return traceRecordRepository
                .findByStatusIgnoreCase(
                        "FAILED"
                );
    }


    // =========================
    // 7. 全系统 Analytics Summary
    // =========================

    public TraceAnalyticsSummary
    getAnalyticsSummary(
            Long slowThresholdMs) {

        long totalTraces =
                traceRecordRepository.countDistinctTraceIds();

        long failedTraces =
                traceRecordRepository
                        .countDistinctFailedTraceIds();

        long slowTraces =
                traceRecordRepository
                        .countDistinctSlowTraceIds(
                                slowThresholdMs
                        );

        double errorRate = 0.0;

        if (totalTraces > 0) {

            errorRate =
                    ((double) failedTraces
                            / totalTraces)
                            * 100;
        }

        Double averageLatency =
                traceRecordRepository
                        .averageRootTraceDuration();

        double averageLatencyMs =
                averageLatency == null ? 0.0 : averageLatency;


        return new TraceAnalyticsSummary(
                totalTraces,
                failedTraces,
                errorRate,
                averageLatencyMs,
                slowTraces
        );
    }


    // =========================
    // 8. 按 Service 分组 Analytics
    // =========================

    public List<ServiceAnalyticsSummary>
    getServiceAnalytics() {

        List<TraceRecord> allTraces =
                traceRecordRepository
                        .findAll();


        // 按 serviceName 分组
        Map<String, List<TraceRecord>> grouped =
                allTraces.stream()
                        .collect(
                                Collectors.groupingBy(
                                        TraceRecord::getServiceName
                                )
                        );


        List<ServiceAnalyticsSummary> result =
                new ArrayList<>();


        // 每一个 service 单独计算
        for (
                Map.Entry<String, List<TraceRecord>> entry
                : grouped.entrySet()
        ) {

            String serviceName =
                    entry.getKey();

            List<TraceRecord> traces =
                    entry.getValue();


            // 请求总数
            long requestCount =
                    traces.size();


            // FAILED 数量
            long failedCount =
                    traces.stream()
                            .filter(
                                    trace ->
                                            "FAILED"
                                                    .equalsIgnoreCase(
                                                            trace.getStatus()
                                                    )
                            )
                            .count();


            // Error Rate
            double errorRate = 0.0;

            if (requestCount > 0) {

                errorRate =
                        ((double) failedCount
                                / requestCount)
                                * 100;
            }


            // 平均耗时
            double averageLatencyMs =
                    traces.stream()
                            .mapToLong(
                                    TraceRecord::getDurationMs
                            )
                            .average()
                            .orElse(0.0);


            // 所有 latency 排序
            List<Long> latencies =
                    traces.stream()
                            .map(
                                    TraceRecord::getDurationMs
                            )
                            .sorted()
                            .toList();


            // P95
            double p95LatencyMs =
                    calculatePercentile(
                            latencies,
                            0.95
                    );


            // P99
            double p99LatencyMs =
                    calculatePercentile(
                            latencies,
                            0.99
                    );


            // 放进 DTO
            result.add(
                    new ServiceAnalyticsSummary(
                            serviceName,
                            requestCount,
                            failedCount,
                            errorRate,
                            averageLatencyMs,
                            p95LatencyMs,
                            p99LatencyMs
                    )
            );
        }


        return result;
    }


    // =========================
    // 9. Percentile 计算
    // =========================

    private double calculatePercentile(
            List<Long> values,
            double percentile) {

        if (values.isEmpty()) {
            return 0.0;
        }

        int index =
                (int) Math.ceil(
                        percentile
                                * values.size()
                ) - 1;


        return values.get(index);
    }
}
