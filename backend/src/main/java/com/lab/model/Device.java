package com.lab.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Device {
    private Long id;
    private String code;
    private String name;
    private Long categoryId;
    private String model;
    private String location;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    /** 关联展示 */
    private String categoryName;
}
