package com.tracepilot.backend.dto;

public class TraceAnalyticsSummary {

    private long totalTraces;
    private long failedTraces;
    private double errorRate;
    private double averageLatencyMs;
    private long slowTraces;

    public TraceAnalyticsSummary(
            long totalTraces,
            long failedTraces,
            double errorRate,
            double averageLatencyMs,
            long slowTraces) {

        this.totalTraces = totalTraces;
        this.failedTraces = failedTraces;
        this.errorRate = errorRate;
        this.averageLatencyMs = averageLatencyMs;
        this.slowTraces = slowTraces;
    }

    public long getTotalTraces() {
        return totalTraces;
    }

    public long getFailedTraces() {
        return failedTraces;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public long getSlowTraces() {
        return slowTraces;
    }
}
