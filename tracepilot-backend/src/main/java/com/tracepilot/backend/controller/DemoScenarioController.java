package com.tracepilot.backend.controller;

import com.tracepilot.backend.dto.DemoScenarioResponse;
import com.tracepilot.backend.service.DemoScenarioService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoScenarioController {

    private final DemoScenarioService demoScenarioService;

    public DemoScenarioController(DemoScenarioService demoScenarioService) {
        this.demoScenarioService = demoScenarioService;
    }

    @PostMapping("/{scenario}")
    public DemoScenarioResponse trigger(@PathVariable String scenario) {
        return demoScenarioService.trigger(scenario);
    }
}
