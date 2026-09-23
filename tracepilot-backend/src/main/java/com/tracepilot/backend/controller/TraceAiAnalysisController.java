package com.tracepilot.backend.controller;

import com.tracepilot.backend.dto.TraceAiAnalysisResponse;
import com.tracepilot.backend.service.TraceAiAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class TraceAiAnalysisController {

    private final TraceAiAnalysisService analysisService;

    public TraceAiAnalysisController(TraceAiAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "available", true,
                "modelConfigured", analysisService.isModelConfigured()
        );
    }

    @PostMapping("/analyze/{traceId}")
    public TraceAiAnalysisResponse analyze(@PathVariable String traceId) {
        return analysisService.analyze(traceId);
    }
}
