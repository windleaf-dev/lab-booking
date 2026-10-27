package com.lab.service;

import com.lab.common.BizException;
import com.lab.common.CacheNames;
import com.lab.common.PageResult;
import com.lab.mapper.DeviceCategoryMapper;
import com.lab.mapper.DeviceMapper;
import com.lab.mapper.StatsMapper;
import com.lab.model.Device;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class DeviceService {

    private static final Set<String> STATUSES = Set.of("AVAILABLE", "MAINTENANCE", "RETIRED");

    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper categoryMapper;
    private final StatsMapper statsMapper;

    public DeviceService(DeviceMapper deviceMapper, DeviceCategoryMapper categoryMapper, StatsMapper statsMapper) {
        this.deviceMapper = deviceMapper;
        this.categoryMapper = categoryMapper;
        this.statsMapper = statsMapper;
    }

    @Cacheable(cacheNames = CacheNames.DEVICE_PAGE)
    public PageResult<Device> page(String keyword, Long categoryId, String status, int page, int size) {
        return PageResult.of(
                deviceMapper.count(keyword, categoryId, status),
                deviceMapper.page(keyword, categoryId, status, (page - 1) * size, size));
    }

    public Device get(long id) {
        Device device = deviceMapper.findById(id);
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }
        return device;
    }

    @CacheEvict(cacheNames = {CacheNames.DEVICE_PAGE, CacheNames.CATEGORIES,
            CacheNames.STATS_CATEGORY, CacheNames.STATS_OVERVIEW}, allEntries = true)
    public Device create(Device req) {
        validate(req);
        if (deviceMapper.countByCode(req.getCode(), 0) > 0) {
            throw BizException.badRequest("设备编号已存在");
        }
        deviceMapper.insert(req);
        return get(req.getId());
    }

    @CacheEvict(cacheNames = {CacheNames.DEVICE_PAGE, CacheNames.CATEGORIES,
            CacheNames.STATS_CATEGORY, CacheNames.STATS_OVERVIEW}, allEntries = true)
    public Device update(long id, Device req) {
        if (deviceMapper.findById(id) == null) {
            throw BizException.notFound("设备不存在");
        }
        validate(req);
        if (deviceMapper.countByCode(req.getCode(), id) > 0) {
            throw BizException.badRequest("设备编号已存在");
        }
        req.setId(id);
        deviceMapper.update(req);
        return get(id);
    }

    @CacheEvict(cacheNames = {CacheNames.DEVICE_PAGE, CacheNames.CATEGORIES,
            CacheNames.STATS_CATEGORY, CacheNames.STATS_OVERVIEW}, allEntries = true)
    public void delete(long id) {
        if (deviceMapper.findById(id) == null) {
            throw BizException.notFound("设备不存在");
        }
        long count = statsMapper.countBookingsByDevice(id);
        if (count > 0) {
            throw BizException.badRequest("该设备已有 " + count + " 条预约记录，无法删除，可改为「停用」");
        }
        deviceMapper.deleteById(id);
    }

    private void validate(Device req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw BizException.badRequest("设备编号不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw BizException.badRequest("设备名称不能为空");
        }
        if (req.getCategoryId() == null || categoryMapper.findById(req.getCategoryId()) == null) {
            throw BizException.badRequest("请选择有效的设备分类");
        }
        if (req.getStatus() == null || !STATUSES.contains(req.getStatus())) {
            throw BizException.badRequest("设备状态只能是 AVAILABLE、MAINTENANCE 或 RETIRED");
        }
        req.setCode(req.getCode().trim());
        req.setName(req.getName().trim());
    }
}
