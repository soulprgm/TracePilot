package com.tracepilot.inventoryservice.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InventoryController {

    @GetMapping("/inventory/test")
    public Map<String, Object> testInventory() {
        return Map.of(
                "status", "available",
                "warehouse", "primary"
        );
    }
}
