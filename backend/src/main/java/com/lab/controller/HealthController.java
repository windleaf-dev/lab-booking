package com.lab.controller;

import com.lab.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "UP", "service", "lab-booking-backend"));
    }
}
