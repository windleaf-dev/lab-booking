package com.lab.controller;

import com.lab.common.AuthContext;
import com.lab.common.Result;
import com.lab.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginReq(String username, String password) {
    }

    public record PasswordReq(String oldPassword, String newPassword) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginReq req) {
        return Result.ok(authService.login(req.username(), req.password()));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.profile(AuthContext.require().uid()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    /** 修改自己的登录密码 */
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody PasswordReq req) {
        authService.changePassword(AuthContext.require().uid(), req.oldPassword(), req.newPassword());
        return Result.ok();
    }
}
