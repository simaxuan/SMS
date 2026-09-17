package com.exam.controller;

import com.exam.common.R;
import com.exam.dto.GradeRequest;
import com.exam.entity.Grade;
import com.exam.service.GradeService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grades")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    public R<List<Grade>> list(@RequestParam(required = false) Long examId,
                               @RequestParam(required = false) Long courseId,
                               @RequestParam(required = false) Long studentId) {
        return R.ok(gradeService.list(examId, courseId, studentId));
    }

    @PostMapping
    public R<Grade> create(@Valid @RequestBody GradeRequest req) {
        return R.ok(gradeService.create(req));
    }

    @PostMapping("/batch")
    public R<Map<String, Object>> batch(@Valid @RequestBody List<GradeRequest> reqs) {
        int count = gradeService.batchCreate(reqs);
        return R.ok(Map.of("created", count));
    }

    @PutMapping("/{id}")
    public R<Grade> update(@PathVariable Long id, @Valid @RequestBody GradeRequest req) {
        return R.ok(gradeService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        gradeService.delete(id);
        return R.ok();
    }
}
