package com.lab.controller;

import com.lab.common.Result;
import com.lab.mapper.TimeSlotMapper;
import com.lab.model.TimeSlot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class TimeSlotController {

    private final TimeSlotMapper timeSlotMapper;

    public TimeSlotController(TimeSlotMapper timeSlotMapper) {
        this.timeSlotMapper = timeSlotMapper;
    }

    @GetMapping
    public Result<List<TimeSlot>> list() {
        return Result.ok(timeSlotMapper.listAll());
    }
}
