package com.lab.controller;

import com.lab.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后端根路径说明：8091 是纯 API 服务，没有网页界面。
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Result<Map<String, String>> index() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("service", "lab-booking-backend");
        info.put("status", "UP");
        info.put("message", "后端服务运行中，请访问前端 http://127.0.0.1:5174");
        info.put("health", "/api/health");
        return Result.ok(info);
    }
}
