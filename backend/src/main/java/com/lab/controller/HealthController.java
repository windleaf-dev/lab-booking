package com.lab.controller;

import com.lab.common.Result;
import com.lab.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final NotificationService notificationService;

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;

    public HealthController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "lab-booking-backend");
        data.put("redisEnabled", redisEnabled);
        data.put("websocketOnline", notificationService.onlineUsers());
        return Result.ok(data);
    }
}
