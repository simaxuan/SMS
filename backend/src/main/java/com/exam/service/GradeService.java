package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.dto.GradeRequest;
import com.exam.entity.Account;
import com.exam.entity.Grade;
import com.exam.entity.ParentStudentBind;
import com.exam.entity.Student;
import com.exam.repository.GradeRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;
    private final ParentStudentBindRepository bindRepository;
    private final StudentRepository studentRepository;
    private final DataScopeService dataScopeService;

    public GradeService(GradeRepository gradeRepository,
                        ParentStudentBindRepository bindRepository,
                        StudentRepository studentRepository,
                        DataScopeService dataScopeService) {
        this.gradeRepository = gradeRepository;
        this.bindRepository = bindRepository;
        this.studentRepository = studentRepository;
        this.dataScopeService = dataScopeService;
    }

    /** 查询成绩：老师=source teacher，且按可见班级收敛（I-3 读路径）；家长=绑定学生可见(teacher只读+本人parent)。 */
    public List<Grade> list(Long examId, Long courseId, Long studentId) {
        Account user = LoginUserContext.requireLogin();
        if (Account.ROLE_TEACHER.equals(user.getRole())) {
            // 读路径班域收敛：CLASS 老师仅返回其可见班级学生的成绩；SCOPE_SCHOOL 本校全部班；ALL 全校。
            java.util.Set<Long> visible = dataScopeService.visibleClassIds(user);
            // 阶段 B B1：仅当 CLASS/SCHOOL 按班级收敛时放开 school 过滤（传 null，兼容 school_id 为 null 的历史数据）；
            //           SCOPE_ALL（visible==null，无班级收敛）→ schoolFilterForStatistics 对全局超管返回 null（放开全校），
            //           避免被 currentSchoolId()（admin 非空 schoolId=演示学校）误锁在演示学校（修复：与 statistics 同源兜底）。
            Long schoolFilter = (visible == null) ? dataScopeService.schoolFilterForStatistics(user) : null;
            List<Grade> grades = gradeRepository.search(GradeSources.TEACHER, examId, courseId, studentId, schoolFilter);
            if (visible == null) {
                return grades; // ALL 全校（schoolFilterForStatistics 对超管返回 null 放开全校）
            }
            if (visible.isEmpty()) {
                return new ArrayList<>(); // CLASS 无任何绑定班级：无可见成绩
            }
            java.util.Set<Long> visibleStudentIds = studentRepository.findByClassIdInAndDeletedFalse(visible).stream()
                    .map(Student::getId)
                    .collect(java.util.stream.Collectors.toSet());
            return grades.stream()
                    .filter(g -> visibleStudentIds.contains(g.getStudentId()))
                    .collect(java.util.stream.Collectors.toList());
        }
        // 家长：范围=绑定学生；可见= teacher 来源 + 全部 parent 来源（双父母编辑/查看一致）
        // I-6：来源过滤下压 SQL，避免全量拉取后在内存过滤
        List<Long> bound = boundStudentIds(user);
        if (bound.isEmpty()) {
            return new ArrayList<>();
        }
        List<Grade> merged = new ArrayList<>();
        merged.addAll(gradeRepository.findByStudentIdInAndSource(bound, GradeSources.TEACHER));
        merged.addAll(gradeRepository.findByStudentIdInAndSource(bound, GradeSources.PARENT));
        return merged;
    }

    private String sourceOf(Account user) {
        return Account.ROLE_TEACHER.equals(user.getRole()) ? GradeSources.TEACHER : GradeSources.PARENT;
    }

    private void assertScoreNotExceed(GradeRequest req) {
        if (req.getFullScore() != null && req.getScore() != null && req.getScore() > req.getFullScore()) {
            throw new BizException("分数不能超过满分");
        }
    }

    @Transactional
    public Grade create(GradeRequest req) {
        Account user = LoginUserContext.requireLogin();
        String source = sourceOf(user);
        assertScoreNotExceed(req);
        if (gradeRepository.existsByStudentIdAndExamIdAndCourseIdAndSource(
                req.getStudentId(), req.getExamId(), req.getCourseId(), source)) {
            throw new BizException(409, "该学生该考试该课程的成绩已存在，请勿重复录入");
        }
        if (Account.ROLE_TEACHER.equals(user.getRole())) {
            // I-3 班域校验：老师仅可录入其所属班级学生的成绩
            ensureTeacherClassScope(user, req.getStudentId());
        } else {
            ensureBound(user, req.getStudentId());
        }
        Grade g = toEntity(req, user);
        try {
            return gradeRepository.save(g);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发下 existsBy... 检查存在 TOCTOU 竞态，唯一约束兜底冲突统一转友好提示
            throw new BizException(409, "该学生该考试该课程的成绩已存在，请勿重复录入");
        }
    }

    @Transactional
    public Grade update(Long id, GradeRequest req) {
        Account user = LoginUserContext.requireLogin();
        Grade g = gradeRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "成绩不存在"));
        ensureManageable(user, g);
        assertScoreNotExceed(req);
        // 若家长将成绩改挂到其他学生，需对"新学生"重新校验绑定，防止跨绑定越权
        if (!Account.ROLE_TEACHER.equals(user.getRole())
                && !g.getStudentId().equals(req.getStudentId())) {
            ensureBound(user, req.getStudentId());
        }
        // 老师改挂到其他学生：校验新学生属于其可见班级
        if (Account.ROLE_TEACHER.equals(user.getRole())
                && !g.getStudentId().equals(req.getStudentId())) {
            ensureTeacherClassScope(user, req.getStudentId());
        }
        if (gradeRepository.existsByStudentIdAndExamIdAndCourseIdAndSourceAndIdNot(
                req.getStudentId(), req.getExamId(), req.getCourseId(), g.getSource(), id)) {
            throw new BizException("该学生该考试该课程的成绩已存在，请勿重复录入");
        }
        g.setStudentId(req.getStudentId());
        g.setExamId(req.getExamId());
        g.setCourseId(req.getCourseId());
        g.setScore(req.getScore());
        g.setFullScore(req.getFullScore());
        // 八轮强约束：改挂学生后同步刷新 school_id 快照
        studentRepository.findByIdAndDeletedFalse(req.getStudentId())
                .ifPresent(s -> g.setSchoolId(s.getSchoolId()));
        try {
            return gradeRepository.save(g);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发/改挂下唯一键冲突统一转 409（与 create/batchCreate 兜底一致，避免 500）
            throw new BizException(409, "该学生该考试该课程的成绩已存在，请勿重复录入");
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
            // 乐观锁冲突：并发修改同一成绩，提示重试而非静默覆盖
            throw new BizException(409, "该成绩已被其他操作更新，请刷新后重试");
        }
    }

    @Transactional
    public void delete(Long id) {
        Account user = LoginUserContext.requireLogin();
        Grade g = gradeRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "成绩不存在"));
        ensureManageable(user, g);
        gradeRepository.delete(g);
    }

    @Transactional
    public Map<String, Object> batchCreate(List<GradeRequest> reqs) {
        Account user = LoginUserContext.requireLogin();
        String source = sourceOf(user);
        int count = 0;
        int skipped = 0; // 超满分/已存在被跳过行数（宽容跳过，逐行记录）
        // P2 整改：跳过明细——记录被跳过的行及其原因，避免静默掩盖录入丢失
        List<Map<String, Object>> skippedDetails = new ArrayList<>();
        for (GradeRequest req : reqs) {
            if (Account.ROLE_TEACHER.equals(user.getRole())) {
                // I-3 班域校验：老师批量录入仅限所属班级学生
                ensureTeacherClassScope(user, req.getStudentId());
            } else {
                ensureBound(user, req.getStudentId());
            }
            if (req.getFullScore() != null && req.getScore() != null && req.getScore() > req.getFullScore()) {
                skipped++; // 超额分数跳过本次（宽容策略：不中断整批，逐行记录）
                skippedDetails.add(skipDetail(req, "分数超过满分"));
                continue;
            }
            if (!gradeRepository.existsByStudentIdAndExamIdAndCourseIdAndSource(
                    req.getStudentId(), req.getExamId(), req.getCourseId(), source)) {
                try {
                    gradeRepository.save(toEntity(req, user));
                    count++;
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    // 并发下 exists 检查存在 TOCTOU 竞态：唯一约束冲突视为已存在，跳过而非报 500
                    skipped++;
                    skippedDetails.add(skipDetail(req, "并发唯一约束冲突"));
                }
            } else {
                skipped++;
                skippedDetails.add(skipDetail(req, "成绩已存在"));
            }
        }
        if (skipped > 0) {
            org.slf4j.LoggerFactory.getLogger(GradeService.class)
                    .info("批量录入跳过 {} 行（超满分/已存在/并发冲突），成功 {} 行", skipped, count);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("created", count);
        result.put("skipped", skipped);
        result.put("skippedDetails", skippedDetails);
        return result;
    }

    /** 构造单行跳过明细（studentId/examId/courseId/reason），便于前端/调用方定位丢失行。 */
    private Map<String, Object> skipDetail(GradeRequest req, String reason) {
        Map<String, Object> d = new HashMap<>();
        d.put("studentId", req.getStudentId());
        d.put("examId", req.getExamId());
        d.put("courseId", req.getCourseId());
        d.put("reason", reason);
        return d;
    }

    // ---------- 权限辅助 ----------

    /** 校验当前用户可管理该成绩记录。 */
    private void ensureManageable(Account user, Grade g) {
        if (Account.ROLE_TEACHER.equals(user.getRole())) {
            if (!GradeSources.TEACHER.equals(g.getSource())) {
                throw new BizException(403, "老师不可修改家长录入的成绩");
            }
            // I-3 班域校验：老师仅可操作其所属班级学生的成绩
            ensureTeacherClassScope(user, g.getStudentId());
            return;
        }
        // 双父母编辑一致（v5确认）：家长可编辑/删除「绑定学生」的 parent 来源成绩（不限本人录入）
        if (!GradeSources.PARENT.equals(g.getSource())) {
            throw new BizException(403, "家长不可修改老师录入的成绩");
        }
        ensureBound(user, g.getStudentId());
    }

    /** I-3：老师写操作——目标学生必须属于其可见班级；越界抛 403。 */
    private void ensureTeacherClassScope(Account user, Long studentId) {
        if (!Account.ROLE_TEACHER.equals(user.getRole())) return;
        Student s = studentRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> new BizException(404, "学生不存在"));
        if (!dataScopeService.canManageClass(user, s.getClassId())) {
            throw new BizException(403, "无权操作该班级学生的成绩");
        }
    }

    private void ensureBound(Account user, Long studentId) {
        if (!bindRepository.existsByParentAccountIdAndStudentId(user.getId(), studentId)) {
            throw new BizException(403, "仅可操作已绑定学生的成绩");
        }
    }

    private List<Long> boundStudentIds(Account user) {
        return bindRepository.findByParentAccountId(user.getId()).stream()
                .map(ParentStudentBind::getStudentId)
                .collect(Collectors.toList());
    }

    private Grade toEntity(GradeRequest req, Account user) {
        Grade g = new Grade();
        g.setStudentId(req.getStudentId());
        g.setExamId(req.getExamId());
        g.setCourseId(req.getCourseId());
        g.setScore(req.getScore());
        g.setFullScore(req.getFullScore());
        g.setSource((Account.ROLE_TEACHER.equals(user.getRole()) ? GradeSources.TEACHER : GradeSources.PARENT));
        g.setCreatorAccountId(user.getId());
        // 八轮强约束：冗余 school_id 快照（取目标学生归属；自建/未入学可为 null）
        studentRepository.findByIdAndDeletedFalse(req.getStudentId()).ifPresent(s -> g.setSchoolId(s.getSchoolId()));
        return g;
    }
}
