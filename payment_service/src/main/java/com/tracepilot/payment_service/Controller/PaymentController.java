package com.tracepilot.payment_service.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    @GetMapping("/payment/test")
    public String testPayment() throws InterruptedException {

        Thread.sleep(2000);

        return "payment-service is running";
    }
    @GetMapping("/payment/fail")
    public String failPayment() {
        throw new RuntimeException("Payment processing failed");
    }
}