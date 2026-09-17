package com.tracepilot.backend.controller;

import com.google.protobuf.ByteString;
import com.tracepilot.backend.service.OtlpTraceIngestionService;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OtlpTraceControllerTests {

    @Mock
    private OtlpTraceIngestionService ingestionService;

    @Test
    void acceptsGzipCompressedOtlpPayloads() throws IOException {
        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder().setSchemaUrl("test-schema"))
                .build();
        OtlpTraceController controller = new OtlpTraceController(ingestionService);

        var response = controller.receiveTraces(gzip(request.toByteArray()), "gzip");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/x-protobuf");
        assertThat(response.getBody()).isEqualTo(ByteString.EMPTY.toByteArray());

        ArgumentCaptor<ExportTraceServiceRequest> requestCaptor =
                ArgumentCaptor.forClass(ExportTraceServiceRequest.class);
        verify(ingestionService).ingest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isEqualTo(request);
    }

    private static byte[] gzip(byte[] payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(payload);
        }
        return output.toByteArray();
    }
}
