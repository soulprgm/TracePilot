package com.tracepilot.orderservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class OrderController {

    private final RestTemplate restTemplate;
    private final String inventoryBaseUrl;
    private final String paymentBaseUrl;

    public OrderController(
            RestTemplate restTemplate,
            @Value("${services.inventory.base-url:http://localhost:8083}") String inventoryBaseUrl,
            @Value("${services.payment.base-url:http://localhost:8082}") String paymentBaseUrl) {
        this.restTemplate = restTemplate;
        this.inventoryBaseUrl = inventoryBaseUrl;
        this.paymentBaseUrl = paymentBaseUrl;
    }

    @GetMapping({"/order/test", "/order/fail"})
    public Map<String, Object> failedOrder() {
        return runOrder("fail", "/payment/fail");
    }

    @GetMapping("/order/success")
    public Map<String, Object> successfulOrder() {
        return runOrder("success", "/payment/success");
    }

    @GetMapping("/order/slow")
    public Map<String, Object> slowOrder() {
        return runOrder("slow", "/payment/slow");
    }

    private Map<String, Object> runOrder(String scenario, String paymentPath) {
        String inventoryResult = restTemplate.getForObject(
                inventoryBaseUrl + "/inventory/test",
                String.class
        );
        String paymentResult = restTemplate.getForObject(
                paymentBaseUrl + paymentPath,
                String.class
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", scenario);
        response.put("status", "completed");
        response.put("inventory", inventoryResult);
        response.put("payment", paymentResult);
        response.put("timestamp", Instant.now());
        return response;
    }
}
