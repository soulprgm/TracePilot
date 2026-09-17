package com.tracepilot.backend.dto;

public class ServiceAnalyticsSummary {

    private String serviceName;
    private long requestCount;
    private long failedCount;
    private double errorRate;
    private double averageLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;

    public ServiceAnalyticsSummary(
            String serviceName,
            long requestCount,
            long failedCount,
            double errorRate,
            double averageLatencyMs,
            double p95LatencyMs,
            double p99LatencyMs) {

        this.serviceName = serviceName;
        this.requestCount = requestCount;
        this.failedCount = failedCount;
        this.errorRate = errorRate;
        this.averageLatencyMs = averageLatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
    }

    public String getServiceName() {
        return serviceName;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public double getP95LatencyMs() {
        return p95LatencyMs;
    }

    public double getP99LatencyMs() {
        return p99LatencyMs;
    }
}