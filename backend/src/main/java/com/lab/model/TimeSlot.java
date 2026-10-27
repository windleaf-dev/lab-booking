package com.lab.model;

import lombok.Data;

import java.time.LocalTime;

@Data
public class TimeSlot {
    private Long id;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer sortNo;
}
