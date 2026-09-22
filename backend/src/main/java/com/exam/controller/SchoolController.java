package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.CreateSchoolRequest;
import com.exam.entity.Account;
import com.exam.entity.School;
import com.exam.service.SchoolService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping
    public R<List<School>> list() {
        LoginUserContext.requireLogin();
        return R.ok(schoolService.list());
    }

    private static void requireGlobalAdmin() {
        com.exam.entity.Account a = LoginUserContext.requireRole(Account.ROLE_TEACHER);
        // 学校为多租户根，仅全局超管（SCOPE_ALL 且学号校归属可空或任意）可创建/编辑/删除，普通本校老师不可越权。
        if (!Account.SCOPE_ALL.equals(a.getScopeType())) {
            throw new com.exam.common.BizException(403, "仅全局管理员可维护学校数据");
        }
    }

    @PostMapping
    public R<School> create(@RequestBody CreateSchoolRequest req) {
        requireGlobalAdmin();
        return R.ok(schoolService.create(req));
    }

    @PutMapping("/{id}")
    public R<School> update(@PathVariable Long id, @RequestBody CreateSchoolRequest req) {
        requireGlobalAdmin();
        return R.ok(schoolService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requireGlobalAdmin();
        schoolService.delete(id);
        return R.ok();
    }
}
