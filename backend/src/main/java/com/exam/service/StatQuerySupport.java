package com.exam.service;

import com.exam.common.BizException;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.entity.Account;
import com.exam.entity.Grade;
import com.exam.entity.Student;
import com.exam.repository.ClassRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计/分析业务共享的查询支撑助手，供 {@link StatisticsService} 及其拆分出的各职责服务复用。
 * <p>集中了跨服务共用的：权限可见成绩过滤、按 ID 批量加载（学生/班级，避免 N+1）、
 * 行→统计项/百分比转换，以及统一的统计口径说明常量。
 * <p>纯数值计算仍在 {@link StatMath}；本类只做「取数 + 组装 + 权限」，不做业务编排。
 */
@Component
public class StatQuerySupport {

    /** 统计口径说明（跨统计接口一致），各职责服务组装结果时统一引用。 */
    public static final String STAT_NOTE =
            "统计口径说明：及格线=该科满分×60%，优秀线=满分×85%，分数段按得分率划分（<60%、60-69%、70-79%、80-89%、≥90%）；"
                    + "不同满分（如语文100/120）均已换算为得分率后统计。";

    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final ParentStudentBindRepository bindRepository;
    private final DataScopeService dataScopeService;
    private final StatMath stat;

    public StatQuerySupport(GradeRepository gradeRepository,
                            StudentRepository studentRepository,
                            ClassRepository classRepository,
                            ParentStudentBindRepository bindRepository,
                            DataScopeService dataScopeService,
                            StatMath stat) {
        this.gradeRepository = gradeRepository;
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.bindRepository = bindRepository;
        this.dataScopeService = dataScopeService;
        this.stat = stat;
    }

    /** 当前用户对该学生的可见成绩（老师=仅可见班级学生的teacher来源；家长=绑定学生+teacher+全部parent(双父母一致)）。 */
    public List<Grade> visibleGrades(Account user, Long studentId) {
        if (Account.ROLE_TEACHER.equals(user.getRole())) {
            // I-3 读路径班域：老师仅可查看其可见班级学生的成绩，越班 403
            Student s = studentRepository.findByIdAndDeletedFalse(studentId)
                    .orElseThrow(() -> new BizException(404, "学生不存在"));
            // #5 多租户读兜底：登录用户有归属校时，目标学生必须同校（否则可越权读跨校学生成绩）。
            // SCOPE_ALL 带登录校同样受归属约束（按登录校收敛）；true-global（schoolId 为空）放开。
            if (user.getSchoolId() != null
                    && (s.getSchoolId() == null || !user.getSchoolId().equals(s.getSchoolId()))) {
                throw new BizException(403, "无权查看该学生的成绩");
            }
            if (!dataScopeService.canManageClass(user, s.getClassId())) {
                throw new BizException(403, "无权查看该学生的成绩");
            }
            return gradeRepository.findByStudentId(studentId).stream()
                    .filter(g -> GradeSources.TEACHER.equals(g.getSource()))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (!bindRepository.existsByParentAccountIdAndStudentId(user.getId(), studentId)) {
            throw new BizException(403, "仅可查看已绑定学生的成绩");
        }
        return gradeRepository.findByStudentId(studentId).stream()
                .filter(g -> GradeSources.TEACHER.equals(g.getSource()) || GradeSources.PARENT.equals(g.getSource()))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 一次性按 ID 加载学生（避免 N+1）。 */
    public Map<Long, Student> studentByIds(List<Long> ids) {
        Map<Long, Student> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        studentRepository.findAllById(ids).forEach(s -> map.put(s.getId(), s));
        return map;
    }

    /** 一次性按 ID 加载班级名映射（避免 N+1）。 */
    public Map<Long, String> classNameByIds(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        classRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }

    /** 一次性按 ID 加载 班级ID→级部ID 映射（排名 scope=level 分组用）。 */
    public Map<Long, Long> classLevelByIds(java.util.Collection<Long> classIds) {
        Map<Long, Long> map = new HashMap<>();
        if (classIds == null || classIds.isEmpty()) return map;
        classRepository.findAllById(classIds).forEach(c -> map.put(c.getId(), c.getLevelId()));
        return map;
    }

    /** 行→统计项（原始分/满分/得分率%）。ScoreRow 投影：score/fullScore 具名字段读取。 */
    public List<StatMath.ScoreItem> toScoreItems(List<com.exam.dto.ScoreRow> scores) {
        List<StatMath.ScoreItem> items = new ArrayList<>();
        for (com.exam.dto.ScoreRow row : scores) {
            int score = row.score() == null ? 0 : row.score();
            int full = row.safeFullScore();
            items.add(stat.item(score, full));
        }
        return items;
    }

    /** 行得分率：CourseRow 投影具名字段读取（rankRows/courseRowsByExam 组装保证）。 */
    public double percentOf(com.exam.dto.CourseRow row) {
        int score = row.score() == null ? 0 : row.score();
        int full = row.safeFullScore();
        return score * 100.0 / full;
    }

    /** 空安全字符串化（排名/展示用）。 */
    public String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 计算班级/范围聚合（供家长 aggregate 档位，不含他人身份）。 */
    public Map<String, Object> classAggregate(List<Map<String, Object>> rows) {
        Map<String, Object> agg = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            agg.put("total", 0);
            agg.put("max", 0);
            agg.put("min", 0);
            agg.put("avgPercent", 0);
            return agg;
        }
        int total = rows.size();
        int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
        double psum = 0;
        for (Map<String, Object> m : rows) {
            int s = ((Number) m.get("score")).intValue();
            if (s > max) max = s;
            if (s < min) min = s;
            psum += ((Number) m.get("percent")).doubleValue();
        }
        agg.put("total", total);
        agg.put("max", max);
        agg.put("min", min);
        agg.put("avgPercent", total > 0 ? stat.round1(psum / total) : 0);
        return agg;
    }
}
