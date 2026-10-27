package com.lab.service;

import com.lab.mapper.BookingMapper;
import com.lab.model.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 预约状态自动流转。
 *
 * <p>独立成 Bean 的原因：
 * 1. {@code @Transactional} 依赖 Spring 代理，同类内部调用不会生效；
 * 2. 定时任务与「打开列表时的懒刷新」复用同一套逻辑；
 * 3. 每次更新都带 {@code status = expectedStatus}，多实例 / 重复执行天然幂等。
 */
@Service
public class BookingStatusService {

    private static final Logger log = LoggerFactory.getLogger(BookingStatusService.class);

    private final BookingMapper bookingMapper;
    private final StatsCacheEvictor statsCacheEvictor;
    private final NotificationService notificationService;

    public BookingStatusService(BookingMapper bookingMapper,
                                StatsCacheEvictor statsCacheEvictor,
                                NotificationService notificationService) {
        this.bookingMapper = bookingMapper;
        this.statsCacheEvictor = statsCacheEvictor;
        this.notificationService = notificationService;
    }

    /**
     * @return 本次实际流转的预约数量（0 表示没有变化）
     */
    @Transactional
    public int refreshStatuses() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        int changed = 0;

        // 已通过且时段结束：签到过 → 完成；未签到 → 违约
        for (Booking booking : bookingMapper.findFinishedApproved(today, now)) {
            boolean checked = booking.getCheckinAt() != null;
            String status = checked ? "COMPLETED" : "VIOLATED";
            String remark = checked ? "已完成" : "未签到，系统判定违约";

            if (bookingMapper.finishBySystem(booking.getId(), status, remark, "APPROVED") > 0) {
                changed++;
                notificationService.notifyUser(
                        booking.getUserId(),
                        checked ? "BOOKING_COMPLETED" : "BOOKING_VIOLATED",
                        checked ? "预约已完成" : "预约已违约",
                        checked
                                ? describe(booking) + " 已结束，签到时间 " + booking.getCheckinAt()
                                : describe(booking) + " 未在签到窗口内签到，已记录一次违约",
                        booking.getId());
            }
        }

        // 待审批但时段已开始：自动取消，释放设备
        for (Booking booking : bookingMapper.findExpiredPending(today, now)) {
            if (bookingMapper.finishBySystem(booking.getId(), "CANCELLED", "超时未审批，系统自动取消", "PENDING") > 0) {
                changed++;
                notificationService.notifyUser(
                        booking.getUserId(),
                        "BOOKING_CANCELLED",
                        "预约已自动取消",
                        describe(booking) + " 在时段开始前未完成审批，系统已自动取消",
                        booking.getId());
                notificationService.notifyPendingChanged(booking.getId());
            }
        }

        if (changed > 0) {
            statsCacheEvictor.evictStats();
            log.info("预约状态自动流转完成：{} 条", changed);
        }
        return changed;
    }

    private String describe(Booking booking) {
        return (booking.getDeviceName() == null ? "设备" : booking.getDeviceName())
                + " · " + booking.getBookingDate()
                + " " + (booking.getSlotName() == null ? "" : booking.getSlotName());
    }
}
