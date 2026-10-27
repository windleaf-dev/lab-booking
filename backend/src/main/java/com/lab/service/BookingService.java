package com.lab.service;

import com.lab.common.BizException;
import com.lab.common.PageResult;
import com.lab.mapper.BookingMapper;
import com.lab.mapper.DeviceMapper;
import com.lab.mapper.TimeSlotMapper;
import com.lab.model.Booking;
import com.lab.model.Device;
import com.lab.model.TimeSlot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final BookingStatusService bookingStatusService;
    private final StatsCacheEvictor statsCacheEvictor;
    private final NotificationService notificationService;
    private final RedisLock redisLock;

    public BookingService(BookingMapper bookingMapper, DeviceMapper deviceMapper,
                          TimeSlotMapper timeSlotMapper, BookingStatusService bookingStatusService,
                          StatsCacheEvictor statsCacheEvictor, NotificationService notificationService,
                          RedisLock redisLock) {
        this.bookingMapper = bookingMapper;
        this.deviceMapper = deviceMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.bookingStatusService = bookingStatusService;
        this.statsCacheEvictor = statsCacheEvictor;
        this.notificationService = notificationService;
        this.redisLock = redisLock;
    }

    /**
     * 创建预约：
     * 1. Redis 分布式锁做前置削峰（未启用 Redis 时自动跳过）
     * 2. 数据库唯一索引 active_key 做最终保障
     * 3. 事务提交后再推送 WebSocket，避免回滚后发出假通知
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

        // 锁粒度：同一设备 + 同一天 + 同一时段；不同设备 / 时段互不影响
        String lockKey = "lock:booking:" + deviceId + "_" + date + "_" + slotId;
        String lockToken = redisLock.tryLock(lockKey);
        if (lockToken == null) {
            throw BizException.badRequest("该时段正在被其他同学抢约，请稍后重试");
        }

        try {
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
            bookingMapper.insert(booking);

            Booking saved = bookingMapper.findById(booking.getId());
            statsCacheEvictor.evictStats();
            notificationService.notifyStaff(
                    "PENDING_CREATED",
                    "新的预约待审批",
                    saved.getRealName() + " 提交了 " + describe(saved),
                    saved.getId());
            notificationService.notifyUser(
                    userId,
                    "BOOKING_CREATED",
                    "预约已提交",
                    "已提交 " + describe(saved) + "，等待教师审批",
                    saved.getId());
            return saved;
        } catch (DuplicateKeyException e) {
            throw BizException.badRequest("该设备的这个时段已被预约，请选择其他时段");
        } finally {
            // 事务结束后再释放锁，避免提交前被其他请求抢到
            redisLock.unlockAfterCompletion(lockKey, lockToken);
        }
    }

    /** 某天的全部有效预约，用于前端渲染「设备 × 时段」矩阵 */
    public List<Booking> listByDate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        bookingStatusService.refreshStatuses();
        return bookingMapper.listByDate(date);
    }

    public PageResult<Booking> mine(long userId, String status, int page, int size) {
        bookingStatusService.refreshStatuses();
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

        statsCacheEvictor.evictStats();
        if ("PENDING".equals(booking.getStatus())) {
            notificationService.notifyPendingChanged(id);
        }
        notificationService.notifyUser(
                userId,
                "BOOKING_CANCELLED",
                "预约已取消",
                "你取消了 " + describe(booking),
                id);
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
        bookingStatusService.refreshStatuses();
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

        statsCacheEvictor.evictStats();
        notificationService.notifyPendingChanged(id);
        notificationService.notifyUser(
                booking.getUserId(),
                approved ? "BOOKING_APPROVED" : "BOOKING_REJECTED",
                approved ? "预约已通过" : "预约被驳回",
                "你对 " + describe(booking) + " 的预约" + (approved ? "已通过" : "被驳回")
                        + (cleanRemark == null || cleanRemark.isBlank() ? "" : "，备注：" + cleanRemark),
                id);
    }

    private Booking requireOwned(long userId, long id) {
        Booking booking = bookingMapper.findById(id);
        if (booking == null || booking.getUserId() != userId) {
            throw BizException.notFound("预约记录不存在");
        }
        return booking;
    }

    private String describe(Booking booking) {
        String device = booking.getDeviceName() == null
                ? "设备 #" + booking.getDeviceId()
                : booking.getDeviceName();
        String slot = booking.getSlotName() == null ? "" : " " + booking.getSlotName();
        return device + " · " + booking.getBookingDate() + slot;
    }
}
