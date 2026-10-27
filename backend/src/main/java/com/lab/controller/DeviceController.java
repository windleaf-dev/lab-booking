package com.lab.controller;

import com.lab.common.AuthContext;
import com.lab.common.PageResult;
import com.lab.common.Result;
import com.lab.model.Device;
import com.lab.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/page")
    public Result<PageResult<Device>> page(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(deviceService.page(keyword, categoryId, status, page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public Result<Device> get(@PathVariable long id) {
        return Result.ok(deviceService.get(id));
    }

    @PostMapping
    public Result<Device> create(@RequestBody Device req) {
        AuthContext.requireStaff();
        return Result.ok(deviceService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Device> update(@PathVariable long id, @RequestBody Device req) {
        AuthContext.requireStaff();
        return Result.ok(deviceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        AuthContext.requireStaff();
        deviceService.delete(id);
        return Result.ok();
    }

    @GetMapping("/options")
    public Result<Map<String, List<String>>> options() {
        return Result.ok(Map.of(
                "statuses", List.of("AVAILABLE", "MAINTENANCE", "RETIRED")));
    }
}
