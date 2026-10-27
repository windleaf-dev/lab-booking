package com.lab.controller;

import com.lab.common.AuthContext;
import com.lab.common.PageResult;
import com.lab.common.Result;
import com.lab.model.User;
import com.lab.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record CreateReq(String username, String password, String realName, String role, Integer status) {
    }

    public record UpdateReq(String realName, String role, Integer status) {
    }

    public record ResetReq(String password) {
    }

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/page")
    public Result<PageResult<User>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        AuthContext.requireAdmin();
        return Result.ok(userService.page(keyword, role, page, Math.min(size, 100)));
    }

    @PostMapping
    public Result<User> create(@RequestBody CreateReq req) {
        AuthContext.requireAdmin();
        return Result.ok(userService.create(req.username(), req.password(), req.realName(),
                req.role(), req.status()));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable long id, @RequestBody UpdateReq req) {
        AuthContext.requireAdmin();
        return Result.ok(userService.update(AuthContext.require().uid(), id,
                req.realName(), req.role(), req.status()));
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable long id, @RequestBody(required = false) ResetReq req) {
        AuthContext.requireAdmin();
        userService.resetPassword(id, req == null ? null : req.password());
        return Result.ok();
    }
}
