package com.lab.config;

import com.lab.mapper.BookingMapper;
import com.lab.mapper.DeviceCategoryMapper;
import com.lab.mapper.DeviceMapper;
import com.lab.mapper.TimeSlotMapper;
import com.lab.mapper.UserMapper;
import com.lab.model.Booking;
import com.lab.model.Device;
import com.lab.model.DeviceCategory;
import com.lab.model.TimeSlot;
import com.lab.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首次启动初始化演示数据（幂等：各表为空才写入）。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserMapper userMapper;
    private final DeviceCategoryMapper categoryMapper;
    private final DeviceMapper deviceMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final BookingMapper bookingMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(UserMapper userMapper, DeviceCategoryMapper categoryMapper,
                           DeviceMapper deviceMapper, TimeSlotMapper timeSlotMapper,
                           BookingMapper bookingMapper, PasswordEncoder passwordEncoder,
                           JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.deviceMapper = deviceMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.bookingMapper = bookingMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userMapper.countAll() == 0) {
            createUser("admin", "系统管理员", "ADMIN");
            createUser("teacher", "张老师", "TEACHER");
            createUser("student", "李同学", "STUDENT");
            log.info("已初始化默认账号：admin / teacher / student（密码均为 123456）");
        }

        if (categoryMapper.countAll() == 0) {
            createCategory("PC", "计算机设备", "服务器、工作站、笔记本");
            createCategory("EI", "电子仪器", "示波器、分析仪、电源");
            createCategory("IM", "智能制造", "3D 打印、激光切割");
            createCategory("ME", "会议设备", "投影、视频会议");
            log.info("已初始化 4 个设备分类");
        }

        if (timeSlotMapper.countAll() == 0) {
            createSlot("08:00-10:00", "08:00", "10:00", 1);
            createSlot("10:00-12:00", "10:00", "12:00", 2);
            createSlot("14:00-16:00", "14:00", "16:00", 3);
            createSlot("16:00-18:00", "16:00", "18:00", 4);
            log.info("已初始化 4 个预约时间段");
        }

        if (deviceMapper.countAll() == 0) {
            Map<String, DeviceCategory> categories = categoryMapper.listAll().stream()
                    .collect(Collectors.toMap(DeviceCategory::getCode, Function.identity()));
            createDevice("D001", "高性能服务器", categories.get("PC"), "Dell R750", "A301", "AVAILABLE", "8 卡 GPU 训练服务器");
            createDevice("D002", "图形工作站", categories.get("PC"), "HP Z8 G5", "A302", "AVAILABLE", "三维建模与渲染");
            createDevice("D003", "频谱分析仪", categories.get("EI"), "Keysight N9020B", "B201", "AVAILABLE", "射频信号分析");
            createDevice("D004", "数字示波器", categories.get("EI"), "Tektronix MSO54", "B202", "MAINTENANCE", "5 系列混合信号示波器");
            createDevice("D005", "3D 打印机", categories.get("IM"), "Prusa MK4", "C101", "AVAILABLE", "FDM 打印");
            createDevice("D006", "激光切割机", categories.get("IM"), "xTool P2", "C102", "AVAILABLE", "非金属激光切割");
            createDevice("D007", "工程投影仪", categories.get("ME"), "Epson CB-2255U", "D401", "AVAILABLE", "5000 流明");
            createDevice("D008", "视频会议终端", categories.get("ME"), "Poly Studio X50", "D402", "AVAILABLE", "中小型会议室");
            log.info("已初始化 8 台演示设备");
        }

        seedDemoBookings();
    }

    /**
     * 预约演示数据：让首次打开看板就有趋势、状态分布和待审批数据。
     * 仅在 booking 表为空时执行。
     */
    private void seedDemoBookings() {
        if (bookingMapper.countAllBookings() > 0) {
            return;
        }
        User admin = userMapper.findByUsername("admin");
        User teacher = userMapper.findByUsername("teacher");
        User student = userMapper.findByUsername("student");
        List<TimeSlot> slots = timeSlotMapper.listAll();
        if (admin == null || teacher == null || student == null || slots.size() < 4) {
            return;
        }

        LocalDate today = LocalDate.now();

        // 过去 6 天的已完成记录，用于趋势图
        for (int i = 1; i <= 6; i++) {
            LocalDate date = today.minusDays(i);
            seedBooking(admin, "D002", slots.get(0), date, "COMPLETED", "三维建模课程实验");
            seedBooking(teacher, "D005", slots.get(1), date, "COMPLETED", "创新实践打印模型");
            if (i % 2 == 0) {
                seedBooking(student, "D006", slots.get(2), date, "COMPLETED", "结构件切割");
            }
        }

        // 一条违约记录
        seedBooking(student, "D003", slots.get(2), today.minusDays(2), "VIOLATED", "射频信号测试");

        // 待审批与已通过
        seedBooking(teacher, "D007", slots.get(3), today.plusDays(1), "PENDING", "课题组汇报");
        seedBooking(student, "D001", slots.get(0), today.plusDays(2), "APPROVED", "模型训练");

        // 今天已结束的时段补一条完成记录
        slots.stream()
                .filter(slot -> slot.getEndTime().isBefore(LocalTime.now()))
                .findFirst()
                .ifPresent(slot -> seedBooking(student, "D006", slot, today, "COMPLETED", "今日实验"));

        log.info("已初始化预约演示数据");

        // 让演示数据的审批 / 签到时间落在预约日期附近，避免出现「先签到、后创建」的穿越感
        jdbcTemplate.update("""
                update booking b join time_slot s on s.id = b.slot_id
                set b.approved_at = timestamp(date_sub(b.booking_date, interval 1 day), '09:00:00'),
                    b.checkin_at = case
                        when b.status = 'COMPLETED'
                        then timestamp(b.booking_date, s.start_time) - interval 5 minute
                        else b.checkin_at
                    end
                where b.status in ('COMPLETED', 'VIOLATED')
                """);
    }

    private void seedBooking(User user, String deviceCode, TimeSlot slot, LocalDate date,
                             String targetStatus, String purpose) {
        Device device = deviceMapper.findByCode(deviceCode);
        User admin = userMapper.findByUsername("admin");
        if (device == null || slot == null || admin == null) {
            return;
        }

        Booking booking = new Booking();
        booking.setUserId(user.getId());
        booking.setDeviceId(device.getId());
        booking.setSlotId(slot.getId());
        booking.setBookingDate(date);
        booking.setPurpose(purpose);
        booking.setStatus("PENDING");
        booking.setActiveKey(device.getId() + "_" + date + "_" + slot.getId());
        bookingMapper.insert(booking);

        if (!"PENDING".equals(targetStatus)) {
            bookingMapper.approve(booking.getId(), admin.getId(), "演示数据自动审批");
        }
        if ("COMPLETED".equals(targetStatus)) {
            bookingMapper.checkin(booking.getId(), user.getId());
            bookingMapper.finishBySystem(booking.getId(), "COMPLETED", "已完成");
        } else if ("VIOLATED".equals(targetStatus)) {
            bookingMapper.finishBySystem(booking.getId(), "VIOLATED", "未签到，系统判定违约");
        }
    }

    private void createUser(String username, String realName, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
    }

    private void createCategory(String code, String name, String remark) {
        DeviceCategory category = new DeviceCategory();
        category.setCode(code);
        category.setName(name);
        category.setRemark(remark);
        categoryMapper.insert(category);
    }

    private void createSlot(String name, String start, String end, int sortNo) {
        TimeSlot slot = new TimeSlot();
        slot.setName(name);
        slot.setStartTime(LocalTime.parse(start));
        slot.setEndTime(LocalTime.parse(end));
        slot.setSortNo(sortNo);
        timeSlotMapper.insert(slot);
    }

    private void createDevice(String code, String name, DeviceCategory category, String model,
                              String location, String status, String description) {
        if (category == null) {
            return;
        }
        Device device = new Device();
        device.setCode(code);
        device.setName(name);
        device.setCategoryId(category.getId());
        device.setModel(model);
        device.setLocation(location);
        device.setStatus(status);
        device.setDescription(description);
        deviceMapper.insert(device);
    }
}
