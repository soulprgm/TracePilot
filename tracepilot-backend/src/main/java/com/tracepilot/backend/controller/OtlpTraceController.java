package com.tracepilot.backend.controller;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import com.tracepilot.backend.service.OtlpTraceIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

@RestController
public class OtlpTraceController {

    private static final MediaType PROTOBUF = MediaType.parseMediaType("application/x-protobuf");

    private final OtlpTraceIngestionService ingestionService;

    public OtlpTraceController(OtlpTraceIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(
            path = "/v1/traces",
            consumes = {"application/x-protobuf", "application/protobuf"},
            produces = "application/x-protobuf"
    )
    public ResponseEntity<byte[]> receiveTraces(
            @RequestBody byte[] payload,
            @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding) {
        try {
            byte[] decodedPayload = decodePayload(payload, contentEncoding);
            ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(decodedPayload);
            ingestionService.ingest(request);
            byte[] response = ExportTraceServiceResponse.getDefaultInstance().toByteArray();
            return ResponseEntity.ok().contentType(PROTOBUF).body(response);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body is not a valid OTLP protobuf trace payload",
                    exception
            );
        }
    }

    private byte[] decodePayload(byte[] payload, String contentEncoding) throws IOException {
        if (contentEncoding == null || contentEncoding.isBlank()
                || "identity".equalsIgnoreCase(contentEncoding)) {
            return payload;
        }
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
                return gzip.readAllBytes();
            }
        }
        throw new IOException("Unsupported Content-Encoding: " + contentEncoding);
    }
}
