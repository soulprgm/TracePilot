package com.tracepilot.payment_service.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    @GetMapping({"/payment/test", "/payment/slow"})
    public Map<String, Object> slowPayment(
            @RequestParam(defaultValue = "2000") long delayMs) throws InterruptedException {
        long safeDelay = Math.max(0, Math.min(delayMs, 5000));
        Thread.sleep(safeDelay);
        return Map.of(
                "status", "approved",
                "scenario", "slow",
                "delayMs", safeDelay
        );
    }

    @GetMapping("/payment/success")
    public Map<String, Object> successfulPayment() {
        return Map.of(
                "status", "approved",
                "scenario", "success"
        );
    }
    @GetMapping("/payment/fail")
    public String failPayment() {
        throw new RuntimeException("Payment processing failed");
    }
}
