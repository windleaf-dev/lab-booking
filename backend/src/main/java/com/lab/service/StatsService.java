package com.lab.service;

import com.lab.common.CacheNames;
import com.lab.mapper.DeviceCategoryMapper;
import com.lab.mapper.DeviceMapper;
import com.lab.mapper.StatsMapper;
import com.lab.mapper.UserMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final StatsMapper statsMapper;

    public StatsService(DeviceMapper deviceMapper, DeviceCategoryMapper categoryMapper,
                        UserMapper userMapper, StatsMapper statsMapper) {
        this.deviceMapper = deviceMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.statsMapper = statsMapper;
    }

    @Cacheable(cacheNames = CacheNames.STATS_OVERVIEW, sync = true)
    public Map<String, Object> overview() {
        long deviceCount = deviceMapper.countAll();
        long availableCount = deviceMapper.countByStatus("AVAILABLE");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceCount", deviceCount);
        data.put("availableCount", availableCount);
        data.put("maintenanceCount", deviceMapper.countByStatus("MAINTENANCE"));
        data.put("categoryCount", categoryMapper.countAll());
        data.put("userCount", userMapper.countAll());
        data.put("bookingCount", statsMapper.countBookings());

        data.put("pendingCount", statsMapper.countByStatus("PENDING"));
        data.put("approvedCount", statsMapper.countByStatus("APPROVED"));
        data.put("todayCount", statsMapper.countTodayBookings());
        data.put("violatedCount", statsMapper.countByStatus("VIOLATED"));
        data.put("completedCount", statsMapper.countByStatus("COMPLETED"));
        data.put("availableRate", deviceCount == 0 ? 0 : Math.round(availableCount * 100.0 / deviceCount));
        return data;
    }

    @Cacheable(cacheNames = CacheNames.STATS_CATEGORY)
    public List<Map<String, Object>> deviceByCategory() {
        return normalize(statsMapper.deviceByCategory());
    }

    @Cacheable(cacheNames = CacheNames.STATS_STATUS)
    public List<Map<String, Object>> statusDistribution() {
        return normalize(statsMapper.statusDistribution());
    }

    @Cacheable(cacheNames = CacheNames.STATS_TOP)
    public List<Map<String, Object>> topDevices() {
        return normalize(statsMapper.topDevices());
    }

    /** 最近 N 天预约趋势，缺失日期补 0，保证折线连续 */
    @Cacheable(cacheNames = CacheNames.STATS_TREND)
    public List<Map<String, Object>> bookingTrend(int days) {
        int span = Math.max(1, Math.min(days, 30));
        LocalDate start = LocalDate.now().minusDays(span - 1L);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : statsMapper.bookingTrend(start)) {
            counts.put(String.valueOf(toLocalDate(row.get("date"))), toLong(row.get("value")));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate date = start.plusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("label", date.format(LABEL_FORMAT));
            item.put("value", counts.getOrDefault(date.toString(), 0L));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            Object name = row.get("name");
            item.put("name", name == null ? "未知" : String.valueOf(name));
            item.put("value", toLong(row.get("value")));
            result.add(item);
        }
        return result;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }
}
