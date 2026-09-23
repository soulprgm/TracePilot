package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.SpanView;
import com.tracepilot.backend.dto.TraceAiAnalysisResponse;
import com.tracepilot.backend.dto.TraceDetailResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceAiAnalysisServiceTests {

    private final TraceDetailService traceDetailService = mock(TraceDetailService.class);
    private final TraceAiAnalysisService service = new TraceAiAnalysisService(
            traceDetailService,
            new ObjectMapper(),
            mock(HttpClient.class),
            true,
            "",
            "gpt-6-luna",
            "https://api.openai.com/v1",
            5,
            30
    );

    @Test
    void identifiesDeepestFailedSpanAsProbableCause() {
        TraceDetailResponse trace = trace("failed-trace", 420,
                span("order", "root", "", 420, "ERROR", 500),
                span("payment", "charge", "root-id", 170, "ERROR", 500));
        when(traceDetailService.getTrace("failed-trace")).thenReturn(trace);

        TraceAiAnalysisResponse result = service.analyze("failed-trace");

        assertThat(result.analysisMode()).isEqualTo("BUILT_IN");
        assertThat(result.severity()).isEqualTo("HIGH");
        assertThat(result.probableRootCause()).contains("payment").contains("charge");
        assertThat(result.evidence()).hasSize(2);
    }

    @Test
    void identifiesLongestSpanInSlowTrace() {
        TraceDetailResponse trace = trace("slow-trace", 2_300,
                span("order", "root", "", 2_300, "OK", 200),
                span("payment", "charge", "root-id", 2_050, "OK", 200));
        when(traceDetailService.getTrace("slow-trace")).thenReturn(trace);

        TraceAiAnalysisResponse result = service.analyze("slow-trace");

        assertThat(result.severity()).isEqualTo("MEDIUM");
        assertThat(result.probableRootCause()).contains("payment").contains("charge");
        assertThat(result.recommendations()).isNotEmpty();
    }

    @Test
    void reportsHealthyTraceWithoutInventingFailure() {
        TraceDetailResponse trace = trace("healthy-trace", 180,
                span("order", "root", "", 180, "OK", 200));
        when(traceDetailService.getTrace("healthy-trace")).thenReturn(trace);

        TraceAiAnalysisResponse result = service.analyze("healthy-trace");

        assertThat(result.severity()).isEqualTo("LOW");
        assertThat(result.summary()).contains("未发现错误");
        assertThat(result.probableRootCause()).contains("没有可识别");
    }

    @Test
    void usesOpenAiStructuredOutputWhenConfigured() throws Exception {
        TraceDetailResponse trace = trace("ai-trace", 180,
                span("order", "root", "", 180, "OK", 200));
        when(traceDetailService.getTrace("ai-trace")).thenReturn(trace);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(request).contains("json_schema").contains("trace_analysis").contains("\"store\":false");
            String analysis = "{\"severity\":\"LOW\",\"summary\":\"模型分析完成\","
                    + "\"probableRootCause\":\"未发现故障\",\"evidence\":[\"HTTP 200\"],"
                    + "\"recommendations\":[\"继续观察\"]}";
            String body = new ObjectMapper().writeValueAsString(Map.of("output_text", analysis));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            TraceAiAnalysisService configured = new TraceAiAnalysisService(
                    traceDetailService, new ObjectMapper(), HttpClient.newHttpClient(), true,
                    "test-key", "gpt-6-luna", "http://localhost:" + server.getAddress().getPort(), 5, 30);

            TraceAiAnalysisResponse result = configured.analyze("ai-trace");

            assertThat(result.analysisMode()).isEqualTo("OPENAI");
            assertThat(result.model()).isEqualTo("gpt-6-luna");
            assertThat(result.summary()).isEqualTo("模型分析完成");
        } finally {
            server.stop(0);
        }
    }

    private TraceDetailResponse trace(String traceId, double durationMs, SpanView... spans) {
        List<SpanView> spanList = List.of(spans);
        String status = spanList.stream().anyMatch(span -> "ERROR".equals(span.statusCode())) ? "FAILED" : "SUCCESS";
        return new TraceDetailResponse(traceId, Instant.EPOCH, Instant.EPOCH.plusMillis((long) durationMs),
                durationMs, status, spanList.size(), spanList.stream().map(SpanView::serviceName).distinct().toList(), spanList);
    }

    private SpanView span(String service, String operation, String parent, double duration,
                          String status, Integer httpStatus) {
        return new SpanView(operation + "-id", parent, service, operation, "SERVER", Instant.EPOCH,
                Instant.EPOCH.plusMillis((long) duration), duration, status, httpStatus, Map.of());
    }
}
