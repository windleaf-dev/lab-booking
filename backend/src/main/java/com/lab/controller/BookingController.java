package com.lab.controller;

import com.lab.common.AuthContext;
import com.lab.common.PageResult;
import com.lab.common.Result;
import com.lab.model.Booking;
import com.lab.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    public record CreateReq(Long deviceId, Long slotId, LocalDate date, String purpose) {
    }

    public record ApproveReq(Boolean approved, String remark) {
    }

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** 某天的设备 × 时段占用情况 */
    @GetMapping("/day")
    public Result<List<Booking>> day(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(bookingService.listByDate(date));
    }

    /** 我的预约 */
    @GetMapping("/my")
    public Result<PageResult<Booking>> mine(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        long userId = AuthContext.require().uid();
        return Result.ok(bookingService.mine(userId, status, page, Math.min(size, 100)));
    }

    /** 创建预约 */
    @PostMapping
    public Result<Booking> create(@RequestBody CreateReq req) {
        long userId = AuthContext.require().uid();
        return Result.ok(bookingService.create(userId, req.deviceId(), req.slotId(), req.date(), req.purpose()));
    }

    /** 取消预约 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable long id) {
        bookingService.cancel(AuthContext.require().uid(), id);
        return Result.ok();
    }

    /** 签到 */
    @PostMapping("/{id}/checkin")
    public Result<Void> checkin(@PathVariable long id) {
        bookingService.checkin(AuthContext.require().uid(), id);
        return Result.ok();
    }

    /** 审批列表（管理员 / 教师） */
    @GetMapping("/pending")
    public Result<PageResult<Booking>> pending(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireStaff();
        return Result.ok(bookingService.pageAll("PENDING", null, null, page, Math.min(size, 100)));
    }

    /** 审批通过 / 驳回 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable long id, @RequestBody ApproveReq req) {
        AuthContext.requireStaff();
        bookingService.approve(
                AuthContext.require().uid(),
                id,
                req.approved() == null || req.approved(),
                req.remark());
        return Result.ok();
    }

    /** 预约管理 / 审批历史（管理员 / 教师） */
    @GetMapping("/page")
    public Result<PageResult<Booking>> page(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireStaff();
        return Result.ok(bookingService.pageAll(status, keyword, date, page, Math.min(size, 100)));
    }
}
