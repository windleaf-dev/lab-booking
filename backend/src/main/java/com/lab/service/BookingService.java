package com.lab.service;

import com.lab.common.BizException;
import com.lab.common.PageResult;
import com.lab.mapper.BookingMapper;
import com.lab.mapper.DeviceMapper;
import com.lab.mapper.StatsMapper;
import com.lab.mapper.TimeSlotMapper;
import com.lab.model.Booking;
import com.lab.model.Device;
import com.lab.model.TimeSlot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingService {

    /** 最多提前预约 14 天 */
    public static final int MAX_ADVANCE_DAYS = 14;
    /** 允许提前签到的时间（分钟） */
    public static final int CHECKIN_AHEAD_MINUTES = 30;

    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "APPROVED");

    private final BookingMapper bookingMapper;
    private final DeviceMapper deviceMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final StatsMapper statsMapper;

    public BookingService(BookingMapper bookingMapper, DeviceMapper deviceMapper,
                          TimeSlotMapper timeSlotMapper, StatsMapper statsMapper) {
        this.bookingMapper = bookingMapper;
        this.deviceMapper = deviceMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.statsMapper = statsMapper;
    }

    /**
     * 创建预约。同一设备 + 日期 + 时段由数据库唯一索引 active_key 兜底，
     * 并发下只有一条能插入成功。
     */
    @Transactional
    public Booking create(long userId, Long deviceId, Long slotId, LocalDate date, String purpose) {
        if (deviceId == null || slotId == null || date == null) {
            throw BizException.badRequest("请选择设备、日期和时段");
        }

        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw BizException.badRequest("不能预约过去的日期");
        }
        if (date.isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw BizException.badRequest("最多只能提前 " + MAX_ADVANCE_DAYS + " 天预约");
        }

        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }
        if (!"AVAILABLE".equals(device.getStatus())) {
            throw BizException.badRequest("该设备当前不可预约（" + device.getStatus() + "）");
        }

        TimeSlot slot = timeSlotMapper.findById(slotId);
        if (slot == null) {
            throw BizException.notFound("预约时段不存在");
        }

        LocalDateTime start = LocalDateTime.of(date, slot.getStartTime());
        if (!start.isAfter(LocalDateTime.now())) {
            throw BizException.badRequest("该时段已经开始，请选择更晚的时段");
        }

        if (bookingMapper.countUserSlot(userId, date, slotId) > 0) {
            throw BizException.badRequest("你在该时段已有预约，不能重复预约");
        }

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setDeviceId(deviceId);
        booking.setSlotId(slotId);
        booking.setBookingDate(date);
        booking.setPurpose(purpose == null ? null : purpose.trim());
        booking.setStatus("PENDING");
        booking.setActiveKey(deviceId + "_" + date + "_" + slotId);

        try {
            bookingMapper.insert(booking);
        } catch (DuplicateKeyException e) {
            throw BizException.badRequest("该设备的这个时段已被预约，请选择其他时段");
        }
        return bookingMapper.findById(booking.getId());
    }

    /** 某天的全部有效预约，用于前端渲染「设备 × 时段」矩阵 */
    public List<Booking> listByDate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        refreshStatuses();
        return bookingMapper.listByDate(date);
    }

    public PageResult<Booking> mine(long userId, String status, int page, int size) {
        refreshStatuses();
        return PageResult.of(
                bookingMapper.countMine(userId, status),
                bookingMapper.pageMine(userId, status, (page - 1) * size, size));
    }

    @Transactional
    public void cancel(long userId, long id) {
        Booking booking = requireOwned(userId, id);
        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw BizException.badRequest("当前状态不可取消");
        }
        LocalDateTime start = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
        if (!start.isAfter(LocalDateTime.now())) {
            throw BizException.badRequest("预约已经开始，无法取消");
        }
        if (bookingMapper.cancel(id, userId) == 0) {
            throw BizException.badRequest("取消失败，请刷新后重试");
        }
    }

    /** 签到：预约开始前 30 分钟到结束前 */
    @Transactional
    public void checkin(long userId, long id) {
        Booking booking = requireOwned(userId, id);
        if (!"APPROVED".equals(booking.getStatus())) {
            throw BizException.badRequest("只有已通过的预约可以签到");
        }
        if (booking.getCheckinAt() != null) {
            throw BizException.badRequest("已经签到过了");
        }

        LocalDateTime start = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
        LocalDateTime end = LocalDateTime.of(booking.getBookingDate(), booking.getEndTime());
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(start.minusMinutes(CHECKIN_AHEAD_MINUTES))) {
            throw BizException.badRequest("还未到签到时间，可在开始前 " + CHECKIN_AHEAD_MINUTES + " 分钟内签到");
        }
        if (now.isAfter(end)) {
            throw BizException.badRequest("该时段已结束，无法签到");
        }
        if (bookingMapper.checkin(id, userId) == 0) {
            throw BizException.badRequest("签到失败，请刷新后重试");
        }
    }

    public PageResult<Booking> pageAll(String status, String keyword, LocalDate date, int page, int size) {
        refreshStatuses();
        return PageResult.of(
                bookingMapper.countAll(status, keyword, date),
                bookingMapper.pageAll(status, keyword, date, (page - 1) * size, size));
    }

    @Transactional
    public void approve(long staffId, long id, boolean approved, String remark) {
        Booking booking = bookingMapper.findById(id);
        if (booking == null) {
            throw BizException.notFound("预约记录不存在");
        }
        if (!"PENDING".equals(booking.getStatus())) {
            throw BizException.badRequest("该预约已被处理，请刷新列表");
        }
        if (booking.getUserId() == staffId) {
            throw BizException.badRequest("不能审批自己提交的预约");
        }
        LocalDateTime start = LocalDateTime.of(booking.getBookingDate(), booking.getStartTime());
        if (!start.isAfter(LocalDateTime.now())) {
            throw BizException.badRequest("该预约时段已开始，无法审批");
        }

        String cleanRemark = remark == null ? null : remark.trim();
        int rows = approved
                ? bookingMapper.approve(id, staffId, cleanRemark)
                : bookingMapper.reject(id, staffId, cleanRemark);
        if (rows == 0) {
            throw BizException.badRequest("审批失败，请刷新后重试");
        }
    }

    /**
     * 状态自动流转：
     * - 已通过但未签到且时段结束 → 违约
     * - 已通过且已签到、时段结束 → 已完成
     * - 待审批但时段已开始 → 自动取消
     */
    @Transactional
    public void refreshStatuses() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        for (Booking booking : bookingMapper.findFinishedApproved(today, now)) {
            if (booking.getCheckinAt() != null) {
                bookingMapper.finishBySystem(booking.getId(), "COMPLETED", "已完成");
            } else {
                bookingMapper.finishBySystem(booking.getId(), "VIOLATED", "未签到，系统判定违约");
            }
        }

        for (Booking booking : bookingMapper.findExpiredPending(today, now)) {
            bookingMapper.finishBySystem(booking.getId(), "CANCELLED", "超时未审批，系统自动取消");
        }
    }

    private Booking requireOwned(long userId, long id) {
        Booking booking = bookingMapper.findById(id);
        if (booking == null || booking.getUserId() != userId) {
            throw BizException.notFound("预约记录不存在");
        }
        return booking;
    }

    /** 给统计模块复用 */
    public Map<String, Object> quickNumbers() {
        return Map.of(
                "pending", statsMapper.countByStatus("PENDING"),
                "today", statsMapper.countTodayBookings(),
                "violated", statsMapper.countByStatus("VIOLATED"));
    }
}
