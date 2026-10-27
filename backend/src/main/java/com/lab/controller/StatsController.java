package com.lab.controller;

import com.lab.common.Result;
import com.lab.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @GetMapping("/device-by-category")
    public Result<List<Map<String, Object>>> deviceByCategory() {
        return Result.ok(statsService.deviceByCategory());
    }

    @GetMapping("/status-distribution")
    public Result<List<Map<String, Object>>> statusDistribution() {
        return Result.ok(statsService.statusDistribution());
    }

    @GetMapping("/booking-trend")
    public Result<List<Map<String, Object>>> bookingTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.bookingTrend(days));
    }

    @GetMapping("/top-devices")
    public Result<List<Map<String, Object>>> topDevices() {
        return Result.ok(statsService.topDevices());
    }
}
