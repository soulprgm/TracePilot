package com.tracepilot.backend.service;

import com.tracepilot.backend.dto.DemoScenarioResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class DemoScenarioService {

    private static final Map<String, String> SCENARIO_PATHS = Map.of(
            "success", "/order/success",
            "slow", "/order/slow",
            "fail", "/order/fail"
    );

    private final String orderServiceUrl;
    private final HttpClient httpClient;

    @Autowired
    public DemoScenarioService(
            @Value("${tracepilot.order-url:http://localhost:8081}") String orderServiceUrl) {
        this(orderServiceUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    DemoScenarioService(String orderServiceUrl, HttpClient httpClient) {
        this.orderServiceUrl = orderServiceUrl.replaceAll("/+$", "");
        this.httpClient = httpClient;
    }

    public DemoScenarioResponse trigger(String requestedScenario) {
        String scenario = requestedScenario == null
                ? ""
                : requestedScenario.trim().toLowerCase(Locale.ROOT);
        String path = SCENARIO_PATHS.get(scenario);
        if (path == null) {
            throw new IllegalArgumentException("scenario must be success, slow, or fail");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(orderServiceUrl + path))
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            boolean expected = "fail".equals(scenario)
                    ? response.statusCode() >= 500
                    : response.statusCode() >= 200 && response.statusCode() < 300;
            return new DemoScenarioResponse(
                    scenario,
                    response.statusCode(),
                    expected,
                    expected ? "Scenario completed; telemetry is being processed" : "Scenario returned an unexpected status",
                    Instant.now()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo request was interrupted", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order service is currently unavailable", exception);
        }
    }
}
