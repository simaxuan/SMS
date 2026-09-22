package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.CourseRequest;
import com.exam.dto.CreateClassRequest;
import com.exam.dto.NameRequest;
import com.exam.entity.Account;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
import com.exam.entity.ExamType;
import com.exam.service.DictionaryService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/classes")
    public R<List<ClassEntity>> listClasses(@RequestParam(required = false) Long schoolId) {
        return R.ok(dictionaryService.listClasses(schoolId));
    }

    @PostMapping("/classes")
    public R<ClassEntity> createClass(@Valid @RequestBody CreateClassRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(dictionaryService.createClass(req));
    }

    @PutMapping("/classes/{id}")
    public R<ClassEntity> updateClass(@PathVariable Long id, @Valid @RequestBody CreateClassRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(dictionaryService.updateClass(id, req));
    }

    @DeleteMapping("/classes/{id}")
    public R<Void> deleteClass(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        dictionaryService.deleteClass(id);
        return R.ok();
    }

    @GetMapping("/courses")
    public R<List<Course>> listCourses() {
        return R.ok(dictionaryService.listCourses());
    }

    @PostMapping("/courses")
    public R<Course> createCourse(@Valid @RequestBody CourseRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(dictionaryService.createCourse(req));
    }

    @PutMapping("/courses/{id}")
    public R<Course> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(dictionaryService.updateCourse(id, req));
    }

    @DeleteMapping("/courses/{id}")
    public R<Void> deleteCourse(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        dictionaryService.deleteCourse(id);
        return R.ok();
    }

    @GetMapping("/exam-types")
    public R<List<ExamType>> listExamTypes() {
        return R.ok(dictionaryService.listExamTypes());
    }

    @PostMapping("/exam-types")
    public R<ExamType> createExamType(@Valid @RequestBody NameRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(dictionaryService.createExamType(req));
    }

    @PutMapping("/exam-types/{id}")
    public R<ExamType> updateExamType(@PathVariable Long id, @Valid @RequestBody NameRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(dictionaryService.updateExamType(id, req));
    }

    @DeleteMapping("/exam-types/{id}")
    public R<Void> deleteExamType(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        dictionaryService.deleteExamType(id);
        return R.ok();
    }
}
