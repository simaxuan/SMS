package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.TeacherClassAssignRequest;
import com.exam.entity.Account;
import com.exam.entity.TeacherClass;
import com.exam.service.TeacherClassService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher-classes")
public class TeacherClassController {

    private final TeacherClassService teacherClassService;

    public TeacherClassController(TeacherClassService teacherClassService) {
        this.teacherClassService = teacherClassService;
    }

    @GetMapping
    public R<List<TeacherClass>> query(@RequestParam(required = false) Long teacherAccountId,
                                       @RequestParam(required = false) Long classId) {
        LoginUserContext.requireLogin();
        return R.ok(teacherClassService.query(teacherAccountId, classId));
    }

    @PostMapping("/assign")
    public R<List<TeacherClass>> assign(@RequestBody TeacherClassAssignRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(teacherClassService.assign(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        teacherClassService.remove(id);
        return R.ok();
    }
}
