package com.lab.service;

import com.lab.common.BizException;
import com.lab.mapper.DeviceCategoryMapper;
import com.lab.mapper.DeviceMapper;
import com.lab.model.DeviceCategory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceCategoryService {

    private final DeviceCategoryMapper categoryMapper;
    private final DeviceMapper deviceMapper;

    public DeviceCategoryService(DeviceCategoryMapper categoryMapper, DeviceMapper deviceMapper) {
        this.categoryMapper = categoryMapper;
        this.deviceMapper = deviceMapper;
    }

    public List<DeviceCategory> listAll() {
        return categoryMapper.listAll();
    }

    public DeviceCategory create(DeviceCategory req) {
        validate(req);
        if (categoryMapper.countByCode(req.getCode(), 0) > 0) {
            throw BizException.badRequest("分类编码已存在");
        }
        if (categoryMapper.countByName(req.getName(), 0) > 0) {
            throw BizException.badRequest("分类名称已存在");
        }
        categoryMapper.insert(req);
        return categoryMapper.findById(req.getId());
    }

    public DeviceCategory update(long id, DeviceCategory req) {
        if (categoryMapper.findById(id) == null) {
            throw BizException.notFound("分类不存在");
        }
        validate(req);
        if (categoryMapper.countByCode(req.getCode(), id) > 0) {
            throw BizException.badRequest("分类编码已存在");
        }
        if (categoryMapper.countByName(req.getName(), id) > 0) {
            throw BizException.badRequest("分类名称已存在");
        }
        req.setId(id);
        categoryMapper.update(req);
        return categoryMapper.findById(id);
    }

    public void delete(long id) {
        if (categoryMapper.findById(id) == null) {
            throw BizException.notFound("分类不存在");
        }
        long count = deviceMapper.countByCategory(id);
        if (count > 0) {
            throw BizException.badRequest("该分类下还有 " + count + " 台设备，请先转移或删除设备");
        }
        categoryMapper.deleteById(id);
    }

    private void validate(DeviceCategory req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw BizException.badRequest("分类编码不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw BizException.badRequest("分类名称不能为空");
        }
        req.setCode(req.getCode().trim());
        req.setName(req.getName().trim());
    }
}
