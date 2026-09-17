package com.exam.controller;

import com.exam.common.R;
import com.exam.dto.ExamRequest;
import com.exam.entity.Exam;
import com.exam.service.ExamService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping
    public R<List<Exam>> list() {
        return R.ok(examService.list());
    }

    @GetMapping("/{id}")
    public R<Exam> getById(@PathVariable Long id) {
        return R.ok(examService.getById(id));
    }

    @PostMapping
    public R<Exam> create(@Valid @RequestBody ExamRequest req) {
        return R.ok(examService.create(req));
    }

    @PutMapping("/{id}")
    public R<Exam> update(@PathVariable Long id, @Valid @RequestBody ExamRequest req) {
        return R.ok(examService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return R.ok();
    }
}
