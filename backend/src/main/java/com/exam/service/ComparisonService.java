package com.exam.service;

import com.exam.entity.Account;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多维度对比 + 学期列表职责服务。
 * <p>选定「考试 + 课程」（或「学期 + 课程」）后，按班级分组给出各班的参考人数/平均分/得分率/
 * 及格率/优秀率/分数段分布，并附全校汇总作对照。仅老师来源数据、老师角色可调用。
 * 学期模式：对某学期全部校内考试，每个学生取其最新一次成绩为代表参与班级对比。
 *
 * <p>由 {@link StatisticsService} 门面类拆分而来（原 {@code StatisticsService#comparison/semesters}），逻辑逐字保留。
 */
@Service
public class ComparisonService {

    private final GradeRepository gradeRepository;
    private final ExamRepository examRepository;
    private final CourseRepository courseRepository;
    private final DataScopeService dataScopeService;
    private final StatQuerySupport support;
    private final StatMath stat;

    public ComparisonService(GradeRepository gradeRepository,
                             ExamRepository examRepository,
                             CourseRepository courseRepository,
                             DataScopeService dataScopeService,
                             StatQuerySupport support,
                             StatMath stat) {
        this.gradeRepository = gradeRepository;
        this.examRepository = examRepository;
        this.courseRepository = courseRepository;
        this.dataScopeService = dataScopeService;
        this.support = support;
        this.stat = stat;
    }

    /**
     * 多维度对比：选定「考试 + 课程」（或「学期 + 课程」）后，按班级分组，给出各班级的
     * 参考人数 / 平均分 / 得分率 / 及格率 / 优秀率 / 分数段分布，并附全校汇总作对照。
     * 仅老师来源数据、老师角色可调用。
     * 学期模式：对某学期全部校内考试，每个学生取其最新一次成绩为代表参与班级对比。
     */
    public Map<String, Object> comparison(Long examId, Long courseId, String semester) {
        Account user = LoginUserContext.requireRole(Account.ROLE_TEACHER);
        String source = GradeSources.TEACHER;
        // 阶段 B B1：SQL 层 school 兜底——仅无班级收敛(ALL)按校；CLASS/SCHOOL 放开（兼容历史 school_id 为 null）
        Long schoolFilter = dataScopeService.schoolFilterForStatistics(user);
        // I-3 读路径班域：P1-3 由「取全量后内存过滤」改为 SQL 下压（带 classIds 查询），避免全校拉取
        java.util.Set<Long> visible = dataScopeService.visibleClassIds(user);
        boolean classConverged = visible != null && !visible.isEmpty();

        // 组装行：RankRow（studentId, studentNo, name, classId, score, fullScore）
        List<com.exam.dto.RankRow> rows;
        if (semester != null && !semester.isBlank()) {
            List<Long> examIds = examRepository.findBySourceAndSemester(GradeSources.SCHOOL, semester, schoolFilter).stream()
                    .map(com.exam.entity.Exam::getId).collect(java.util.stream.Collectors.toList());
            if (examIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("examId", null);
                empty.put("courseId", courseId);
                empty.put("semester", semester);
                empty.put("courseName", courseRepository.findById(courseId)
                        .map(com.exam.entity.Course::getName).orElse("未知课程"));
                empty.put("note", StatQuerySupport.STAT_NOTE + "（学期维度：暂无该学期考试成绩）");
                empty.put("classes", new ArrayList<>());
                empty.put("overall", stat.aggregate(new ArrayList<>()));
                return empty;
            }
            rows = semesterRows(courseId, source, examIds, visible, schoolFilter);
        } else if (classConverged) {
            rows = gradeRepository.rankRowsByClassIds(examId, courseId, source, visible, schoolFilter);
        } else {
            rows = gradeRepository.rankRows(examId, courseId, source, schoolFilter);
        }

        Map<Long, List<com.exam.dto.RankRow>> byClass = new java.util.TreeMap<>();
        List<com.exam.dto.RankRow> overall = new ArrayList<>();
        for (com.exam.dto.RankRow r : rows) {
            byClass.computeIfAbsent(r.classId(), k -> new ArrayList<>()).add(r);
            overall.add(r);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("examId", examId);
        result.put("courseId", courseId);
        result.put("semester", semester);
        result.put("courseName", courseRepository.findById(courseId).map(com.exam.entity.Course::getName).orElse("未知课程"));
        result.put("note", StatQuerySupport.STAT_NOTE + (semester != null && !semester.isBlank() ? "（学期维度：该学期多场考试合并，每生取最新一次）" : ""));

        List<Long> cls = byClass.keySet().stream().filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, String> clsMap = support.classNameByIds(cls);
        List<Map<String, Object>> classes = new ArrayList<>();
        for (Map.Entry<Long, List<com.exam.dto.RankRow>> en : byClass.entrySet()) {
            String className = en.getKey() == null ? "未分班" : clsMap.getOrDefault(en.getKey(), "班级" + en.getKey());
            Map<String, Object> cm = stat.aggregate(en.getValue());
            cm.put("className", className);
            cm.put("classId", en.getKey());
            classes.add(cm);
        }
        classes.sort(Comparator.comparing(c -> String.valueOf(c.get("className"))));
        result.put("classes", classes);
        result.put("overall", stat.aggregate(overall));
        return result;
    }

    /** 学期列表（校内考试已配置的学期），供前端学期维度下拉。按登录校隔离（#29）。 */
    public List<String> semesters() {
        com.exam.entity.Account user = LoginUserContext.requireLogin();
        // 修正：全局超管（SCOPE_ALL）放开全校，避免被 user.getSchoolId()（演示学校）误锁
        Long sid = dataScopeService.effectiveSchoolId();
        if (sid != null) {
            return examRepository.findDistinctSemestersBySchool(GradeSources.SCHOOL, sid);
        }
        return examRepository.findDistinctSemesters(GradeSources.SCHOOL);
    }

    /** 学期模式：该课程在指定考试集合下，每学生取其最新一次成绩为代表（按 examDate 最大）。
     * P1-3：visible（可见班级集合）非空时走 SQL 下压查询，避免全校拉取后内存过滤。 */
    private List<com.exam.dto.RankRow> semesterRows(Long courseId, String source, List<Long> examIds,
                                                    java.util.Set<Long> visible, Long schoolFilter) {
        boolean classConverged = visible != null && !visible.isEmpty();
        List<com.exam.dto.CourseRow> all = classConverged
                ? gradeRepository.courseRowsByExamByClassIds(courseId, source, visible, null)
                : gradeRepository.courseRowsByExam(courseId, source, schoolFilter);
        // 考试日期映射：仅加载学期内涉及考试，避免全表扫描
        Map<Long, java.time.LocalDate> examDate = new HashMap<>();
        examRepository.findAllById(examIds).forEach(e -> examDate.put(e.getId(), e.getExamDate()));

        // 仅保留属于指定学期考试集合的行
        List<com.exam.dto.CourseRow> relevant = new ArrayList<>();
        for (com.exam.dto.CourseRow r : all) {
            if (examIds.contains(r.examId())) {
                relevant.add(r);
            }
        }
        // 一次性加载涉及的学生（避免 N+1）
        Map<Long, com.exam.entity.Student> students = support.studentByIds(relevant.stream()
                .map(com.exam.dto.CourseRow::studentId).collect(java.util.stream.Collectors.toList()));

        // 每学生取 examDate 最大的一行作为该学期代表成绩；
        // 用「当前迭代下标」精确记录代表行，避免 examId 顺序与日期顺序不一致时取错。
        Map<Long, Integer> latestIdxByStudent = new LinkedHashMap<>(); // sid -> relevant 下标
        Map<Long, java.time.LocalDate> dateByStudent = new LinkedHashMap<>();
        for (int idx = 0; idx < relevant.size(); idx++) {
            com.exam.dto.CourseRow r = relevant.get(idx);
            Long sid = r.studentId();
            java.time.LocalDate d = examDate.getOrDefault(r.examId(), java.time.LocalDate.MIN);
            java.time.LocalDate prev = dateByStudent.get(sid);
            if (prev == null || d.compareTo(prev) > 0) {
                dateByStudent.put(sid, d);
                latestIdxByStudent.put(sid, idx);
            }
        }

        List<com.exam.dto.RankRow> out = new ArrayList<>();
        for (java.util.Map.Entry<Long, Integer> en : latestIdxByStudent.entrySet()) {
            com.exam.dto.CourseRow r = relevant.get(en.getValue());
            com.exam.entity.Student s = students.get(en.getKey());
            out.add(new com.exam.dto.RankRow(
                    en.getKey(),
                    s == null ? "-" : s.getStudentNo(),
                    s == null ? "未知" : s.getName(),
                    r.classId(),
                    r.score(),
                    r.fullScore()));
        }
        return out;
    }
}
