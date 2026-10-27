package com.lab.service;

import com.lab.common.BizException;
import com.lab.common.PageResult;
import com.lab.mapper.UserMapper;
import com.lab.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private static final Set<String> ROLES = Set.of("ADMIN", "TEACHER", "STUDENT");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<User> page(String keyword, String role, int page, int size) {
        List<User> records = userMapper.page(keyword, role, (page - 1) * size, size);
        records.forEach(user -> user.setPassword(null));
        return PageResult.of(userMapper.count(keyword, role), records);
    }

    public User create(String username, String password, String realName, String role, Integer status) {
        if (username == null || username.isBlank()) {
            throw BizException.badRequest("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw BizException.badRequest("密码至少 6 位");
        }
        if (userMapper.countByUsername(username.trim(), 0) > 0) {
            throw BizException.badRequest("用户名已存在");
        }
        validateRole(role);

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(status == null ? 1 : status);
        userMapper.insert(user);
        user.setPassword(null);
        return user;
    }

    public User update(long operatorId, long id, String realName, String role, Integer status) {
        User existing = userMapper.findById(id);
        if (existing == null) {
            throw BizException.notFound("用户不存在");
        }
        validateRole(role);
        if (id == operatorId && (!role.equals(existing.getRole()) || status == null || status != 1)) {
            throw BizException.badRequest("不能修改自己的角色或禁用自己的账号");
        }
        existing.setRealName(realName);
        existing.setRole(role);
        existing.setStatus(status == null ? 1 : status);
        userMapper.update(existing);
        existing.setPassword(null);
        return existing;
    }

    public void resetPassword(long id, String password) {
        User existing = userMapper.findById(id);
        if (existing == null) {
            throw BizException.notFound("用户不存在");
        }
        String target = password == null || password.isBlank() ? "123456" : password.trim();
        if (target.length() < 6) {
            throw BizException.badRequest("密码至少 6 位");
        }
        userMapper.updatePassword(id, passwordEncoder.encode(target));
    }

    private void validateRole(String role) {
        if (role == null || !ROLES.contains(role)) {
            throw BizException.badRequest("角色只能是 ADMIN、TEACHER 或 STUDENT");
        }
    }
}
