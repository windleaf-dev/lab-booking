package com.lab.service;

import com.lab.common.BizException;
import com.lab.common.JwtUtil;
import com.lab.mapper.UserMapper;
import com.lab.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw BizException.badRequest("请输入用户名和密码");
        }
        User user = userMapper.findByUsername(username.trim());
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("该账号已被禁用");
        }
        String token = jwtUtil.create(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", toProfile(user));
        return result;
    }

    public Map<String, Object> profile(long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.unauthorized("账号不存在");
        }
        return toProfile(user);
    }

    /** 用户自助修改密码：校验原密码后重新 BCrypt 加密 */
    public void changePassword(long userId, String oldPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.unauthorized("账号不存在");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw BizException.badRequest("请输入原密码");
        }
        User withPassword = userMapper.findByUsername(user.getUsername());
        if (withPassword == null || !passwordEncoder.matches(oldPassword, withPassword.getPassword())) {
            throw BizException.badRequest("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw BizException.badRequest("新密码至少 6 位");
        }
        if (newPassword.equals(oldPassword)) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    private Map<String, Object> toProfile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("realName", user.getRealName());
        profile.put("role", user.getRole());
        return profile;
    }
}
