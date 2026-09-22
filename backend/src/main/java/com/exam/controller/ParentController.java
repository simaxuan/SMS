package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.BindRequest;
import com.exam.entity.Account;
import com.exam.service.ParentService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parent")
public class ParentController {

    private final ParentService parentService;

    public ParentController(ParentService parentService) {
        this.parentService = parentService;
    }

    @PostMapping("/bind")
    public R<Map<String, Object>> bind(@Valid @RequestBody BindRequest req) {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(parentService.bind(parent, req));
    }

    /** 认领合并（自建→校园）：使用 学号+姓名+手机号+身份证后8位。 */
    @PostMapping("/claim")
    public R<Map<String, Object>> claim(@Valid @RequestBody BindRequest req) {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(parentService.claimStudent(parent, req));
    }

    /** 个人版家长自建学生（未入学自测）。 */
    @PostMapping("/students")
    public R<Map<String, Object>> createSelfStudent(@RequestBody Map<String, Object> body) {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        String name = String.valueOf(body.getOrDefault("name", ""));
        Long classId = body.get("classId") == null ? null : ((Number) body.get("classId")).longValue();
        String studentNo = body.get("studentNo") == null ? null : String.valueOf(body.get("studentNo"));
        return R.ok(parentService.createSelfStudent(parent, name, classId, studentNo));
    }

    @GetMapping("/binds")
    public R<List<Map<String, Object>>> binds() {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(parentService.listBinds(parent));
    }

    @DeleteMapping("/binds/{studentId}")
    public R<Void> unbind(@PathVariable Long studentId) {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        parentService.unbind(parent, studentId);
        return R.ok();
    }

    /** 家长：读取排名个性化偏好（只看本班/是否并列）。 */
    @GetMapping("/preference")
    public R<Map<String, Object>> getPreference() {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(parentService.getPreference(parent));
    }

    /** 家长：保存排名个性化偏好。 */
    @PutMapping("/preference")
    public R<Map<String, Object>> savePreference(@RequestBody Map<String, Object> body) {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(parentService.savePreference(parent, body));
    }
}
