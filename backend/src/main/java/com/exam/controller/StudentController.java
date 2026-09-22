package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.dto.StudentRequest;
import com.exam.entity.Account;
import com.exam.entity.Student;
import com.exam.service.StudentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public R<PageResult<Student>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return R.ok(PageResult.of(studentService.list(keyword, pageable)));
    }

    @GetMapping("/{id}")
    public R<Student> getById(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(studentService.getById(id));
    }

    @PostMapping
    public R<Student> create(@Valid @RequestBody StudentRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(studentService.create(req));
    }

    @PutMapping("/{id}")
    public R<Student> update(@PathVariable Long id, @Valid @RequestBody StudentRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(studentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        studentService.delete(id);
        return R.ok();
    }
}
