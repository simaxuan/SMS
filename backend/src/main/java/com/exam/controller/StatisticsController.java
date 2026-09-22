package com.exam.controller;

import com.exam.common.R;
import com.exam.service.StatisticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/course")
    public R<Map<String, Object>> courseStats(@RequestParam Long examId,
                                              @RequestParam Long courseId) {
        return R.ok(statisticsService.courseStats(examId, courseId));
    }

    @GetMapping("/student")
    public R<Map<String, Object>> studentMultiSubject(@RequestParam Long examId,
                                                     @RequestParam Long studentId,
                                                     @RequestParam(required = false) Long examCourseGroupId) {
        return R.ok(statisticsService.studentMultiSubject(examId, studentId, examCourseGroupId));
    }

    @GetMapping("/student-trend")
    public R<Map<String, Object>> studentTrend(@RequestParam Long studentId) {
        return R.ok(statisticsService.studentTrend(studentId));
    }

    @GetMapping("/comparison")
    public R<Map<String, Object>> comparison(@RequestParam(required = false) Long examId,
                                             @RequestParam(required = false) Long courseId,
                                             @RequestParam(required = false) String semester) {
        return R.ok(statisticsService.comparison(examId, courseId, semester));
    }

    @GetMapping("/rank")
    public R<Map<String, Object>> rank(@RequestParam Long examId,
                                       @RequestParam Long courseId,
                                       @RequestParam(required = false) Boolean classScope,
                                       @RequestParam(required = false) Boolean showTies,
                                       @RequestParam(required = false) String scope) {
        return R.ok(statisticsService.ranking(examId, courseId, classScope, showTies, scope));
    }

    @GetMapping("/progress")
    public R<Map<String, Object>> progress(@RequestParam Long courseId,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(defaultValue = "false") boolean regress,
                                           @RequestParam(required = false) String semester) {
        return R.ok(statisticsService.progress(courseId, limit, regress, semester));
    }

    @GetMapping("/semesters")
    public R<List<String>> semesters() {
        return R.ok(statisticsService.semesters());
    }
}
