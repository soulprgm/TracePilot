package com.tracepilot.backend.exception;

public class TraceIdNotFoundException extends RuntimeException {

    public TraceIdNotFoundException(String traceId) {
        super("Trace not found with traceId: " + traceId);
    }
}
