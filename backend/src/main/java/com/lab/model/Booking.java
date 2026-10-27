package com.lab.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class Booking {
    private Long id;
    private Long userId;
    private Long deviceId;
    private Long slotId;
    private LocalDate bookingDate;
    private String purpose;
    /** PENDING / APPROVED / REJECTED / CANCELLED / COMPLETED / VIOLATED */
    private String status;
    /** 有效预约唯一键：deviceId_date_slotId，取消/驳回/结束后置空 */
    private String activeKey;
    private Long approverId;
    private String approveRemark;
    private LocalDateTime approvedAt;
    private LocalDateTime checkinAt;
    private LocalDateTime createdAt;

    /* ---------- 联表展示字段 ---------- */
    private String deviceName;
    private String deviceCode;
    private String slotName;
    private LocalTime startTime;
    private LocalTime endTime;
    private String userName;
    private String realName;
    private String approverName;
}
