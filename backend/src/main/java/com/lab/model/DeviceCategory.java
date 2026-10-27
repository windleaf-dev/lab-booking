package com.lab.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceCategory {
    private Long id;
    private String code;
    private String name;
    private String remark;
    private LocalDateTime createdAt;
    /** 列表展示：该分类下设备数量 */
    private Integer deviceCount;
}
