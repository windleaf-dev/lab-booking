package com.lab.controller;

import com.lab.common.AuthContext;
import com.lab.common.Result;
import com.lab.model.DeviceCategory;
import com.lab.service.DeviceCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class DeviceCategoryController {

    private final DeviceCategoryService categoryService;

    public DeviceCategoryController(DeviceCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<DeviceCategory>> listAll() {
        return Result.ok(categoryService.listAll());
    }

    @PostMapping
    public Result<DeviceCategory> create(@RequestBody DeviceCategory req) {
        AuthContext.requireStaff();
        return Result.ok(categoryService.create(req));
    }

    @PutMapping("/{id}")
    public Result<DeviceCategory> update(@PathVariable long id, @RequestBody DeviceCategory req) {
        AuthContext.requireStaff();
        return Result.ok(categoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        AuthContext.requireStaff();
        categoryService.delete(id);
        return Result.ok();
    }
}
