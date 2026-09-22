package com.exam.service;

import com.exam.entity.Account;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.entity.Grade;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.GradeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 纵向趋势类职责服务：进步/退步分析 + 学生个人成绩趋势。
 * <p>progress：同一课程、相邻两次考试（按考试日期）得分率变化，老师端可见，默认取变化绝对值最大的前 limit 名。
 * studentTrend：按考试(时间)排序返回历次考试的总分与各科分数/得分率；老师任意学生、家长仅绑定学生。
 *
 * <p>由 {@link StatisticsService} 门面类拆分而来（原 {@code StatisticsService#progress/studentTrend}），逻辑逐字保留。
 */
@Service
public class TrendService {

    private final GradeRepository gradeRepository;
    private final ExamRepository examRepository;
    private final CourseRepository courseRepository;
    private final DataScopeService dataScopeService;
    private final StatQuerySupport support;
    private final StatMath stat;

    public TrendService(GradeRepository gradeRepository,
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
     * 进步/退步分析：同一课程、相邻两次考试（按考试日期）得分率变化。
     * 老师端可见；默认取变化绝对值最大的前 limit 名，asc=false 返回退步榜在前。
     */
    public Map<String, Object> progress(Long courseId, int limit, boolean regress, String semester) {
        Account user = LoginUserContext.requireRole(Account.ROLE_TEACHER);
        String source = GradeSources.TEACHER;
        // 阶段 B B1：SQL 层 school 兜底——仅无班级收敛(ALL)按校；CLASS/SCHOOL 放开
        Long schoolFilter = dataScopeService.schoolFilterForStatistics(user);
        // I-3 读路径班域：CLASS 老师仅分析其可见班级。P1-3 由原来「取全量后内存过滤」改为 SQL 下压
        // （带 classIds 的 courseRowsByExamByClassIds），避免全校拉取。CourseRow:[examId,studentId,classId,score,fullScore]
        java.util.Set<Long> visible = dataScopeService.visibleClassIds(user);
        List<com.exam.dto.CourseRow> rows;
        if (visible != null && !visible.isEmpty()) {
            rows = gradeRepository.courseRowsByExamByClassIds(courseId, source, visible, null);
        } else {
            rows = gradeRepository.courseRowsByExam(courseId, source, schoolFilter);
        }

        // 可选：仅保留指定学期内的考试
        final java.util.Set<Long> semesterExamIds;
        if (semester != null && !semester.isBlank()) {
            semesterExamIds = examRepository.findBySourceAndSemester(GradeSources.SCHOOL, semester, schoolFilter).stream()
                    .map(com.exam.entity.Exam::getId).collect(java.util.stream.Collectors.toSet());
            rows = rows.stream().filter(r -> semesterExamIds.contains(r.examId()))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            semesterExamIds = java.util.Collections.emptySet();
        }

        // 考试日期映射：仅加载过滤后涉及考试的日期，避免全表扫描 exam 表
        Map<Long, java.time.LocalDate> examDate = new HashMap<>();
        java.util.Set<Long> involvedExamIds = rows.stream().map(com.exam.dto.CourseRow::examId)
                .collect(java.util.stream.Collectors.toSet());
        examRepository.findAllById(involvedExamIds).forEach(e -> examDate.put(e.getId(), e.getExamDate()));

        // 按学生分组，每场取该生成绩
        Map<Long, List<com.exam.dto.CourseRow>> byStudent = new LinkedHashMap<>();
        for (com.exam.dto.CourseRow r : rows) {
            byStudent.computeIfAbsent(r.studentId(), k -> new ArrayList<>()).add(r);
        }
        // 一次性加载学生（避免逐行查库 N+1）
        Map<Long, com.exam.entity.Student> students = support.studentByIds(new ArrayList<>(byStudent.keySet()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, List<com.exam.dto.CourseRow>> en : byStudent.entrySet()) {
            List<com.exam.dto.CourseRow> gre = en.getValue();
            gre.sort(Comparator
                    .comparing((com.exam.dto.CourseRow r) -> examDate.getOrDefault(r.examId(), java.time.LocalDate.MIN),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(com.exam.dto.CourseRow::examId));
            if (gre.size() < 2) continue; // 至少两场才可比较
            com.exam.dto.CourseRow prev = gre.get(gre.size() - 2);
            com.exam.dto.CourseRow last = gre.get(gre.size() - 1);
            double pPercent = support.percentOf(prev);
            double lPercent = support.percentOf(last);
            com.exam.entity.Student s = students.get(en.getKey());
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", en.getKey());
            m.put("studentNo", s == null ? "-" : s.getStudentNo());
            m.put("name", s == null ? "未知" : s.getName());
            m.put("prevPercent", stat.round1(pPercent));
            m.put("lastPercent", stat.round1(lPercent));
            m.put("delta", stat.round1(lPercent - pPercent));
            list.add(m);
        }

        // 按 delta 排序：regress=true 取下降最多（delta 最小）
        list.sort(Comparator.comparingDouble((Map<String, Object> m) -> (double) m.get("delta")));
        if (!regress) {
            java.util.Collections.reverse(list);
        }
        if (list.size() > limit) {
            list = list.subList(0, limit);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("semester", semester);
        result.put("courseName", courseRepository.findById(courseId).map(com.exam.entity.Course::getName).orElse("未知课程"));
        result.put("regress", regress);
        result.put("note", (semester != null && !semester.isBlank() ? "[" + semester + "] " : "")
                + "进步/退步分析：同一课程相邻两次考试得分率变化，delta>0 表示进步。");
        result.put("items", list);
        return result;
    }

    /**
     * 学生个人成绩趋势：按考试(时间)排序，返回历次考试的总分与各科分数/得分率。
     * 老师：任意学生、仅老师来源；家长：仅绑定学生、老师来源(只读)+本人家长来源；总分仅累计老师(校内考试)来源。
     */
    public Map<String, Object> studentTrend(Long studentId) {
        Account user = LoginUserContext.requireLogin();
        List<Grade> grades = support.visibleGrades(user, studentId);

        // 批量加载考试与课程字典：一次性 findAllById 构建 Map，消除逐条 findById 的 N+1 查询
        Set<Long> examIdSet = new HashSet<>();
        Set<Long> courseIdSet = new HashSet<>();
        for (Grade g : grades) {
            examIdSet.add(g.getExamId());
            courseIdSet.add(g.getCourseId());
        }
        Map<Long, com.exam.entity.Exam> examById = new HashMap<>();
        examRepository.findAllById(examIdSet).forEach(e -> examById.put(e.getId(), e));
        Map<Long, com.exam.entity.Course> courseById = new HashMap<>();
        courseRepository.findAllById(courseIdSet).forEach(c -> courseById.put(c.getId(), c));

        Map<Long, List<Grade>> byExam = new LinkedHashMap<>();
        for (Grade g : grades) {
            byExam.computeIfAbsent(g.getExamId(), k -> new ArrayList<>()).add(g);
        }

        List<Map<String, Object>> trendList = new ArrayList<>();
        for (Map.Entry<Long, List<Grade>> entry : byExam.entrySet()) {
            Long examId = entry.getKey();
            List<Grade> list = entry.getValue();
            com.exam.entity.Exam exam = examById.get(examId);
            int totalScore = 0;
            int totalFull = 0;
            List<StatMath.StdItem> stdItems = new ArrayList<>();
            List<Map<String, Object>> courseItems = new ArrayList<>();
            for (Grade g : list) {
                if (GradeSources.TEACHER.equals(g.getSource())) {
                    totalScore += g.getScore();
                    totalFull += (g.getFullScore() == null || g.getFullScore() <= 0 ? 100 : g.getFullScore());
                }
                int full = g.getFullScore() == null || g.getFullScore() <= 0 ? 100 : g.getFullScore();
                double percent = stat.round1(g.getScore() * 100.0 / full);
                // 标准分：收集各科得分率，按权重模式统一计算（A4）
                if (GradeSources.TEACHER.equals(g.getSource())) {
                    stdItems.add(new StatMath.StdItem(g.getCourseId(), percent, full));
                }
                com.exam.entity.Course course = courseById.get(g.getCourseId());
                String courseName = course == null ? "未知课程" : course.getName();
                Map<String, Object> ci = new HashMap<>();
                ci.put("courseId", g.getCourseId());
                ci.put("courseName", courseName);
                ci.put("score", g.getScore());
                ci.put("fullScore", full);
                ci.put("percent", percent);
                ci.put("source", g.getSource());
                courseItems.add(ci);
            }

            Map<String, Object> item = new HashMap<>();
            item.put("examId", examId);
            item.put("examName", exam == null ? ("考试#" + examId) : exam.getName());
            item.put("examDate", exam == null ? null : exam.getExamDate());
            item.put("totalScore", totalScore);
            item.put("totalFull", totalFull);
            item.put("totalPercent", totalFull > 0 ? stat.round1(totalScore * 100.0 / totalFull) : null);
            item.put("stdScore", stdItems.isEmpty() ? null : stat.weightedStdScore(stdItems));
            item.put("courses", courseItems);
            trendList.add(item);
        }

        // 按考试日期排序（无日期则靠后，其次按考试ID）
        trendList.sort(Comparator
                .comparing((Map<String, Object> m) -> (java.time.LocalDate) m.get("examDate"),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(m -> (Long) m.get("examId")));

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("note", StatQuerySupport.STAT_NOTE);
        result.put("trend", trendList);
        return result;
    }
}
