package com.tracepilot.orderservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class OrderController {

    private final RestTemplate restTemplate;

    public OrderController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/order/test")
    public String testOrder() {

        String inventoryResult = restTemplate.getForObject(
                "http://localhost:8083/inventory/test",
                String.class
        );

        String paymentResult = restTemplate.getForObject(
                "http://localhost:8082/payment/fail",
                String.class
        );

        return "order-service -> "
                + inventoryResult
                + " -> "
                + paymentResult;
    }
}