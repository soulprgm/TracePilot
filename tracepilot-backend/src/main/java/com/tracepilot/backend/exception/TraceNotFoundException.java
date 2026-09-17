package com.tracepilot.backend.exception;

public class TraceNotFoundException extends RuntimeException {


    public TraceNotFoundException(Long id) {
        super("Trace not found with id: " + id);
    }
}