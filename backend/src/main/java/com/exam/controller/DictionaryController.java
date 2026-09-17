package com.exam.controller;

import com.exam.common.R;
import com.exam.dto.NameRequest;
import com.exam.entity.ClassEntity;
import com.exam.entity.Course;
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
    public R<List<ClassEntity>> listClasses() {
        return R.ok(dictionaryService.listClasses());
    }

    @PostMapping("/classes")
    public R<ClassEntity> createClass(@Valid @RequestBody NameRequest req) {
        return R.ok(dictionaryService.createClass(req));
    }

    @PutMapping("/classes/{id}")
    public R<ClassEntity> updateClass(@PathVariable Long id, @Valid @RequestBody NameRequest req) {
        return R.ok(dictionaryService.updateClass(id, req));
    }

    @DeleteMapping("/classes/{id}")
    public R<Void> deleteClass(@PathVariable Long id) {
        dictionaryService.deleteClass(id);
        return R.ok();
    }

    @GetMapping("/courses")
    public R<List<Course>> listCourses() {
        return R.ok(dictionaryService.listCourses());
    }

    @PostMapping("/courses")
    public R<Course> createCourse(@Valid @RequestBody NameRequest req) {
        return R.ok(dictionaryService.createCourse(req));
    }

    @PutMapping("/courses/{id}")
    public R<Course> updateCourse(@PathVariable Long id, @Valid @RequestBody NameRequest req) {
        return R.ok(dictionaryService.updateCourse(id, req));
    }

    @DeleteMapping("/courses/{id}")
    public R<Void> deleteCourse(@PathVariable Long id) {
        dictionaryService.deleteCourse(id);
        return R.ok();
    }
}
