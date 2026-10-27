package com.lab.config;

import com.lab.service.BookingStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟刷新预约状态：超时未审批自动取消、结束后未签到判定违约、已签到标记完成。
 */
@Component
public class BookingStatusTask {

    private static final Logger log = LoggerFactory.getLogger(BookingStatusTask.class);

    private final BookingStatusService bookingStatusService;

    public BookingStatusTask(BookingStatusService bookingStatusService) {
        this.bookingStatusService = bookingStatusService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void refresh() {
        try {
            bookingStatusService.refreshStatuses();
        } catch (Exception e) {
            log.warn("预约状态刷新失败：{}", e.getMessage());
        }
    }
}
