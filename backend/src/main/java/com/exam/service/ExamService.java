package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.dto.ExamRequest;
import com.exam.entity.Account;
import com.exam.entity.Course;
import com.exam.entity.Exam;
import com.exam.entity.ExamCourseGroup;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamCourseGroupRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamTypeRepository;
import com.exam.repository.GradeRepository;
import com.exam.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamTypeRepository examTypeRepository;
    private final GradeRepository gradeRepository;
    private final ExamCourseGroupRepository courseGroupRepository;
    private final CourseRepository courseRepository;
    private final DataScopeService dataScopeService;

    public ExamService(ExamRepository examRepository,
                       ExamTypeRepository examTypeRepository,
                       GradeRepository gradeRepository,
                       ExamCourseGroupRepository courseGroupRepository,
                       CourseRepository courseRepository,
                       DataScopeService dataScopeService) {
        this.examRepository = examRepository;
        this.examTypeRepository = examTypeRepository;
        this.gradeRepository = gradeRepository;
        this.courseGroupRepository = courseGroupRepository;
        this.courseRepository = courseRepository;
        this.dataScopeService = dataScopeService;
    }

    private void validateExamType(Long examTypeId) {
        if (examTypeId != null && !examTypeRepository.existsById(examTypeId)) {
            throw new BizException("考试类型不存在");
        }
    }

    public List<Exam> list() {
        // 读路径 school 下推：按登录校收敛（SCOPE_ALL 带登录校同样按校过滤；true-global 放开全校）。
        Long sid = dataScopeService.effectiveSchoolId();
        return sid == null
                ? examRepository.findAllBySourceOrderByIdAsc(GradeSources.SCHOOL)
                : examRepository.findBySchoolIdAndSourceOrderByIdAsc(sid, GradeSources.SCHOOL);
    }

    public List<Exam> listCustom(Long parentAccountId) {
        return examRepository.findAllBySourceAndCreatorAccountId("custom", parentAccountId);
    }

    /** 查找或创建家长的「自定义小测」考试条目（与校内考试解耦）。 */
    public Exam findOrCreateCustom(Long parentAccountId, String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new BizException("小测名称不能为空");
        }
        return examRepository
                .findBySourceAndCreatorAccountIdAndName("custom", parentAccountId, clean)
                .orElseGet(() -> {
                    Exam e = new Exam();
                    e.setName(clean);
                    e.setSource("custom");
                    e.setCreatorAccountId(parentAccountId);
                    e.setExamDate(java.time.LocalDate.now());
                    return examRepository.save(e);
                });
    }

    public Exam getById(Long id) {
        Exam e = examRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "考试不存在"));
        Account user = LoginUserContext.requireLogin();
        // 家长自定义小测仅本人可见，防止凭 id 泄露他人小测
        if ("custom".equals(e.getSource())) {
            if (e.getCreatorAccountId() == null || !e.getCreatorAccountId().equals(user.getId())) {
                throw new BizException(404, "考试不存在");
            }
            return e;
        }
        // #7 school 来源考试：登录用户有归属校时须同校（防 A 校老师读写 B 校考试，含科目组合）。
        // SCOPE_ALL 带登录校同样受归属约束（按登录校收敛）；true-global（schoolId 为空）放开。
        if (user.getSchoolId() != null
                && (e.getSchoolId() == null || !user.getSchoolId().equals(e.getSchoolId()))) {
            throw new BizException(404, "考试不存在");
        }
        return e;
    }

    public Exam create(ExamRequest req) {
        validateExamType(req.getExamTypeId());
        Exam e = new Exam();
        e.setName(req.getName());
        e.setExamTypeId(req.getExamTypeId());
        e.setExamDate(req.getExamDate());
        e.setSemester(req.getSemester());
        e.setSource(GradeSources.SCHOOL);
        // 写路径 school 决策：普通老师锁定登录校；全局超管信任请求体 schoolId，可跨校建考试（与课程/班级同源对齐）
        e.setSchoolId(dataScopeService.resolveWriteSchoolId(req.getSchoolId()));
        return examRepository.save(e);
    }

    public Exam update(Long id, ExamRequest req) {
        Exam e = getById(id);
        validateExamType(req.getExamTypeId());
        e.setName(req.getName());
        e.setExamTypeId(req.getExamTypeId());
        e.setExamDate(req.getExamDate());
        e.setSemester(req.getSemester());
        return examRepository.save(e);
    }

    public void delete(Long id) {
        Exam e = getById(id); // 复用属主校验：custom 仅本人可删，teacher 对 custom 会 404
        if (gradeRepository.existsByExamId(id)) {
            throw new BizException("该考试已被成绩引用，无法删除");
        }
        // 修复删除安全网：考试的科目组合（ECG）为从属配置，级联清理，避免孤儿组合残留
        courseGroupRepository.deleteByExamId(id);
        examRepository.deleteById(id);
    }

    // ---------- 科目组合（多科总分/标准分配置，S1） ----------

    /** 某考试的科目组合（含课程名，默认全科 active=true）。 */
    public List<Map<String, Object>> courseGroups(Long examId) {
        Exam e = getById(examId);
        List<ExamCourseGroup> groups = courseGroupRepository.findByExamId(examId);
        // 未配置时，以该考试已有成绩的课程为默认集合（active=true）
        Map<Long, Boolean> activeMap = new HashMap<>();
        for (ExamCourseGroup g : groups) activeMap.put(g.getCourseId(), g.getActive());
        // 收集涉及课程
        java.util.Set<Long> courseIds = new java.util.HashSet<>(activeMap.keySet());
        for (com.exam.entity.Grade gr : gradeRepository.findByExamId(examId)) {
            courseIds.add(gr.getCourseId());
        }
        Map<Long, Course> courseById = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.findAllById(courseIds).forEach(c -> courseById.put(c.getId(), c));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long cid : courseIds) {
            Course c = courseById.get(cid);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", cid);
            m.put("courseName", c == null ? "未知课程" : c.getName());
            m.put("active", activeMap.getOrDefault(cid, true));
            result.add(m);
        }
        return result;
    }

    /** 批量保存科目组合。body 形如 [{examId, courseId, active}]。 */
    @Transactional
    public List<Map<String, Object>> saveCourseGroups(List<Map<String, Object>> body) {
        if (body == null || body.isEmpty()) {
            throw new BizException("科目组合不能为空");
        }
        Long examId = ((Number) body.get(0).get("examId")).longValue();
        Exam e = getById(examId); // 属主校验 + 学校归属校验（#7）
        courseGroupRepository.deleteByExamId(examId);
        for (Map<String, Object> item : body) {
            Long courseId = ((Number) item.get("courseId")).longValue();
            boolean active = Boolean.TRUE.equals(item.get("active"));
            ExamCourseGroup g = new ExamCourseGroup();
            g.setExamId(examId);
            g.setCourseId(courseId);
            g.setActive(active);
            // #7 courseGroup 归属取考试归属校（而非登录校），避免 A 校老师操作 B 校考试时错挂本校
            g.setSchoolId(e.getSchoolId());
            courseGroupRepository.save(g);
        }
        return courseGroups(examId);
    }
}
