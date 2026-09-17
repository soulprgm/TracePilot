package com.tracepilot.inventoryservice.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {

    @GetMapping("/inventory/test")
    public String testInventory() {
        return "inventory-service is running";
    }
}