package com.exam.controller;

import com.exam.common.LoginUserContext;
import com.exam.common.R;
import com.exam.dto.ExamRequest;
import com.exam.dto.NameRequest;
import com.exam.entity.Account;
import com.exam.entity.Exam;
import com.exam.service.ExamService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

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

    /** 家长：查看本人的自定义小测列表。 */
    @GetMapping("/custom")
    public R<List<Exam>> customList() {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(examService.listCustom(parent.getId()));
    }

    /** 家长：创建/复用「自定义小测」（与校内考试解耦）。 */
    @PostMapping("/custom")
    public R<Exam> customCreate(@Valid @RequestBody NameRequest req) {
        Account parent = LoginUserContext.requireRole(Account.ROLE_PARENT);
        return R.ok(examService.findOrCreateCustom(parent.getId(), req.getName()));
    }

    /** 科目组合：读取某考试参与总分统计的科目集合。 */
    @GetMapping("/course-groups")
    public R<List<Map<String, Object>>> courseGroups(@RequestParam Long examId) {
        return R.ok(examService.courseGroups(examId));
    }

    /** 科目组合：批量保存。body=[{examId, courseId, active}]。 */
    @PutMapping("/course-groups")
    public R<List<Map<String, Object>>> saveCourseGroups(@RequestBody List<Map<String, Object>> body) {
        return R.ok(examService.saveCourseGroups(body));
    }

    @GetMapping("/{id}")
    public R<Exam> getById(@PathVariable Long id) {
        return R.ok(examService.getById(id));
    }

    @PostMapping
    public R<Exam> create(@Valid @RequestBody ExamRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(examService.create(req));
    }

    @PutMapping("/{id}")
    public R<Exam> update(@PathVariable Long id, @Valid @RequestBody ExamRequest req) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        return R.ok(examService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        LoginUserContext.requireRole(Account.ROLE_TEACHER);
        examService.delete(id);
        return R.ok();
    }
}
