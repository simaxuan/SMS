package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.entity.Account;
import com.exam.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 读取全部设置（登录即可读，家长端用于判断排名是否展示）。 */
    @GetMapping
    public R<Map<String, Object>> list() {
        LoginUserContext.requireLogin();
        return R.ok(settingsService.listSettings());
    }

    /** 更新设置（仅老师）。 */
    @PutMapping("/{key}")
    public R<Void> update(@PathVariable String key, @RequestBody Map<String, Object> body) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        settingsService.update(key, body.get("value"));
        return R.ok();
    }
}
