package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.SpanView;
import com.tracepilot.backend.dto.TraceAiAnalysisResponse;
import com.tracepilot.backend.dto.TraceDetailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TraceAiAnalysisService {

    private static final double SLOW_TRACE_MS = 1_000.0;

    private final TraceDetailService traceDetailService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRequestsPerHour;
    private final Map<String, CachedAnalysis> cache = new ConcurrentHashMap<>();
    private final AtomicLong requestWindowStartedAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger requestsInWindow = new AtomicInteger();

    @Autowired
    public TraceAiAnalysisService(
            TraceDetailService traceDetailService,
            ObjectMapper objectMapper,
            @Value("${tracepilot.ai.enabled:true}") boolean enabled,
            @Value("${tracepilot.ai.api-key:}") String apiKey,
            @Value("${tracepilot.ai.model:gpt-6-luna}") String model,
            @Value("${tracepilot.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${tracepilot.ai.timeout-seconds:25}") long timeoutSeconds,
            @Value("${tracepilot.ai.max-requests-per-hour:30}") int maxRequestsPerHour) {
        this(traceDetailService, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .build(), enabled, apiKey, model, baseUrl, timeoutSeconds, maxRequestsPerHour);
    }

    TraceAiAnalysisService(
            TraceDetailService traceDetailService,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            boolean enabled,
            String apiKey,
            String model,
            String baseUrl,
            long timeoutSeconds,
            int maxRequestsPerHour) {
        this.traceDetailService = traceDetailService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-6-luna" : model.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim())
                .replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.maxRequestsPerHour = Math.max(1, maxRequestsPerHour);
    }

    public TraceAiAnalysisResponse analyze(String traceId) {
        TraceDetailResponse trace = traceDetailService.getTrace(traceId);
        TraceAiAnalysisResponse builtIn = builtInAnalysis(trace);
        if (!enabled || apiKey.isBlank()) {
            return builtIn;
        }

        CachedAnalysis cached = cache.get(trace.traceId());
        if (cached != null && cached.createdAt().isAfter(Instant.now().minus(Duration.ofMinutes(10)))) {
            return cached.analysis();
        }
        if (!allowModelRequest()) {
            return builtIn;
        }

        try {
            TraceAiAnalysisResponse analysis = requestModel(trace);
            cache.put(trace.traceId(), new CachedAnalysis(analysis, Instant.now()));
            return analysis;
        } catch (Exception ignored) {
            return builtIn;
        }
    }

    public boolean isModelConfigured() {
        return enabled && !apiKey.isBlank();
    }

    private TraceAiAnalysisResponse requestModel(TraceDetailResponse trace) throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "severity", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")),
                        "summary", Map.of("type", "string"),
                        "probableRootCause", Map.of("type", "string"),
                        "evidence", Map.of("type", "array", "items", Map.of("type", "string")),
                        "recommendations", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("severity", "summary", "probableRootCause", "evidence", "recommendations"),
                "additionalProperties", false
        );

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "trace_analysis");
        format.put("strict", true);
        format.put("schema", schema);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("instructions", "You are an observability incident analyst. Analyze only the supplied trace facts. "
                + "Do not invent services or evidence. Write concise Chinese. Treat a downstream failed span as a likely cause, "
                + "and distinguish confirmed evidence from a probable root cause.");
        requestBody.put("input", objectMapper.writeValueAsString(toModelInput(trace)));
        requestBody.put("text", Map.of("format", format, "verbosity", "low"));
        requestBody.put("max_output_tokens", 900);
        requestBody.put("store", false);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI provider returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String outputText = root.path("output_text").asText("");
        if (outputText.isBlank()) {
            outputText = extractOutputText(root);
        }
        if (outputText.isBlank()) {
            throw new IllegalStateException("AI provider returned no text output");
        }
        JsonNode analysis = objectMapper.readTree(outputText);
        return new TraceAiAnalysisResponse(
                trace.traceId(),
                "OPENAI",
                model,
                analysis.path("severity").asText("MEDIUM"),
                analysis.path("summary").asText(),
                analysis.path("probableRootCause").asText(),
                stringList(analysis.path("evidence")),
                stringList(analysis.path("recommendations")),
                Instant.now()
        );
    }

    private Map<String, Object> toModelInput(TraceDetailResponse trace) {
        List<Map<String, Object>> spans = trace.spans().stream().map(span -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("spanId", span.spanId());
            item.put("parentSpanId", span.parentSpanId());
            item.put("service", span.serviceName());
            item.put("operation", span.operationName());
            item.put("kind", span.spanKind());
            item.put("durationMs", span.durationMs());
            item.put("status", span.statusCode());
            item.put("httpStatus", span.httpStatusCode());
            return item;
        }).toList();
        return Map.of(
                "traceId", trace.traceId(),
                "status", trace.status(),
                "durationMs", trace.durationMs(),
                "services", trace.services(),
                "spans", spans
        );
    }

    private String extractOutputText(JsonNode root) {
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                String text = content.path("text").asText("");
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private TraceAiAnalysisResponse builtInAnalysis(TraceDetailResponse trace) {
        List<SpanView> failed = trace.spans().stream().filter(this::isFailed).toList();
        List<SpanView> nonRootSpans = trace.spans().stream()
                .filter(span -> span.parentSpanId() != null && !span.parentSpanId().isBlank())
                .toList();
        SpanView longest = (nonRootSpans.isEmpty() ? trace.spans() : nonRootSpans).stream()
                .max(Comparator.comparingDouble(SpanView::durationMs))
                .orElse(null);
        List<String> evidence = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        String severity;
        String summary;
        String probableRootCause;

        if (!failed.isEmpty()) {
            SpanView likelyCause = failed.stream()
                    .max(Comparator.comparingInt(span -> depth(span, trace.spans())))
                    .orElse(failed.getLast());
            severity = "HIGH";
            summary = "链路执行失败，共发现 " + failed.size() + " 个错误 span。";
            probableRootCause = likelyCause.serviceName() + " 的 " + likelyCause.operationName()
                    + " 最可能是故障起点。";
            failed.forEach(span -> evidence.add(span.serviceName() + " / " + span.operationName()
                    + " 状态 " + span.statusCode()
                    + (span.httpStatusCode() == null ? "" : "，HTTP " + span.httpStatusCode())));
            recommendations.add("优先检查 " + likelyCause.serviceName() + " 的异常日志与依赖状态。");
            recommendations.add("使用相同 Trace ID 在 Jaeger 中核对错误传播顺序。");
            recommendations.add("修复后重新运行失败场景并确认错误率恢复。 ");
        } else if (trace.durationMs() >= SLOW_TRACE_MS || (longest != null && longest.durationMs() >= SLOW_TRACE_MS)) {
            severity = "MEDIUM";
            summary = "链路成功完成，但耗时超过慢链路阈值。";
            probableRootCause = longest == null ? "需要进一步检查各 span 耗时。"
                    : longest.serviceName() + " 的 " + longest.operationName() + " 占用了最长时间。";
            if (longest != null) {
                evidence.add("最长 span 为 " + longest.serviceName() + " / " + longest.operationName()
                        + "，耗时约 " + Math.round(longest.durationMs()) + " ms。");
            }
            evidence.add("链路总耗时约 " + Math.round(trace.durationMs()) + " ms。");
            recommendations.add("检查最长 span 对应服务的下游调用、数据库与资源使用情况。");
            recommendations.add("对比该服务 P95/P99，确认是单次抖动还是持续退化。");
        } else {
            severity = "LOW";
            summary = "链路成功完成，未发现错误或明显慢调用。";
            probableRootCause = "当前链路没有可识别的故障根因。";
            evidence.add("全部 " + trace.spanCount() + " 个 span 均未报告错误。");
            evidence.add("链路总耗时约 " + Math.round(trace.durationMs()) + " ms。");
            recommendations.add("继续观察错误率与 P95/P99 的趋势变化。");
            recommendations.add("如需验证告警流程，可运行慢请求或失败演示场景。");
        }

        return new TraceAiAnalysisResponse(trace.traceId(), "BUILT_IN", null, severity, summary,
                probableRootCause, evidence, recommendations, Instant.now());
    }

    private boolean isFailed(SpanView span) {
        return "ERROR".equalsIgnoreCase(span.statusCode())
                || (span.httpStatusCode() != null && span.httpStatusCode() >= 500);
    }

    private int depth(SpanView span, List<SpanView> spans) {
        int depth = 0;
        String parent = span.parentSpanId();
        while (parent != null && !parent.isBlank() && depth < spans.size()) {
            String current = parent;
            SpanView parentSpan = spans.stream().filter(item -> current.equals(item.spanId())).findFirst().orElse(null);
            if (parentSpan == null) break;
            depth++;
            parent = parentSpan.parentSpanId();
        }
        return depth;
    }

    private boolean allowModelRequest() {
        long now = System.currentTimeMillis();
        long started = requestWindowStartedAt.get();
        if (now - started >= Duration.ofHours(1).toMillis()
                && requestWindowStartedAt.compareAndSet(started, now)) {
            requestsInWindow.set(0);
        }
        return requestsInWindow.incrementAndGet() <= maxRequestsPerHour;
    }

    private record CachedAnalysis(TraceAiAnalysisResponse analysis, Instant createdAt) {
    }
}
