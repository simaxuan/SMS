package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.CreateLevelRequest;
import com.exam.entity.Account;
import com.exam.entity.Level;
import com.exam.service.LevelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/levels")
public class LevelController {

    private final LevelService levelService;

    public LevelController(LevelService levelService) {
        this.levelService = levelService;
    }

    @GetMapping
    public R<List<Level>> list(@RequestParam(required = false) Long schoolId) {
        LoginUserContext.requireLogin();
        return R.ok(levelService.listBySchool(schoolId));
    }

    @PostMapping
    public R<Level> create(@RequestBody CreateLevelRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(levelService.create(req));
    }

    @PutMapping("/{id}")
    public R<Level> update(@PathVariable Long id, @RequestBody CreateLevelRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(levelService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        levelService.delete(id);
        return R.ok();
    }
}
