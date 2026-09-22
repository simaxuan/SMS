package com.exam.service;

import com.exam.entity.Account;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.entity.ExamCourseGroup;
import com.exam.entity.Grade;
import com.exam.repository.CourseRepository;
import com.exam.repository.ExamCourseGroupRepository;
import com.exam.repository.GradeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计/分析业务的统一门面：对外保持与原类一致的 7 个公开方法签名与 {@value StatQuerySupport#STAT_NOTE} 口径，
 * 使 {@code StatisticsController} 与既有测试零改动。
 * <p>为消除“上帝类”，已按职责拆分：
 * <ul>
 *   <li>{@link RankingService}——班级/单科排名；</li>
 *   <li>{@link ComparisonService}——多维度对比 + 学期列表；</li>
 *   <li>{@link TrendService}——进步/退步分析 + 学生个人趋势；</li>
 *   <li>{@link StatQuerySupport}——跨服务共享的取数/权限/组装助手；</li>
 *   <li>{@link StatMath}——纯数值计算（分数段/聚合/名次/标准分/四舍五入）。</li>
 * </ul>
 * 本类保留与“单一学生横截面”最相关的方法 {@link #courseStats} 与 {@link #studentMultiSubject}，
 * 其余方法逐字委托给拆分服务，行为完全不变。
 *
 * <p>统计口径（重要，防止日后遗忘）：
 * <ol>
 *   <li>及格线 = 满分 × 60%（得分率 ≥ 60% 视为及格；100 分满分 60 分、120 分满分 72 分）；</li>
 *   <li>优秀线 = 满分 × 85%（得分率 ≥ 85%）；</li>
 *   <li>分数段分布按得分率划分：&lt;60%、60–69%、70–79%、80–89%、≥90%；</li>
 *   <li>同一课程不同考试满分可能不同（如语文 100/120），一律换算为「得分率%」（分数/满分×100）后再统计，
 *       因此及格/优秀/分布天然兼容不同满分，无需硬编码绝对分数线。</li>
 * </ol>
 */
@Service
public class StatisticsService {

    private final GradeRepository gradeRepository;
    private final CourseRepository courseRepository;
    private final ExamCourseGroupRepository examCourseGroupRepository;
    private final DataScopeService dataScopeService;
    private final StatQuerySupport support;
    private final StatMath stat;

    private final RankingService rankingService;
    private final ComparisonService comparisonService;
    private final TrendService trendService;

    public StatisticsService(GradeRepository gradeRepository,
                             CourseRepository courseRepository,
                             ExamCourseGroupRepository examCourseGroupRepository,
                             DataScopeService dataScopeService,
                             StatQuerySupport support,
                             StatMath stat,
                             RankingService rankingService,
                             ComparisonService comparisonService,
                             TrendService trendService) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.examCourseGroupRepository = examCourseGroupRepository;
        this.dataScopeService = dataScopeService;
        this.support = support;
        this.stat = stat;
        this.rankingService = rankingService;
        this.comparisonService = comparisonService;
        this.trendService = trendService;
    }

    public Map<String, Object> courseStats(Long examId, Long courseId) {
        // 横截面统计是老师/校园版能力：老师来源数据，且仅限老师角色调用
        Account user = LoginUserContext.requireRole(Account.ROLE_TEACHER);
        // I-3 读路径班域：CLASS 老师仅统计其可见班级；无绑定班级返回空
        java.util.Set<Long> visible = dataScopeService.visibleClassIds(user);
        // 阶段 B：SQL 层全校兜底——仅无班级收敛(ALL)时按校；有班级收敛放开(兼容 school_id=null 历史数据)
        Long schoolId = dataScopeService.schoolFilterForStatistics(user);
        List<com.exam.dto.ScoreRow> rows;
        if (visible != null && visible.isEmpty()) {
            rows = new ArrayList<>();
        } else if (visible == null) {
            rows = gradeRepository.scores(examId, courseId, GradeSources.TEACHER, schoolId);
        } else {
            rows = gradeRepository.scoresByClass(examId, courseId, GradeSources.TEACHER, visible, schoolId);
        }
        com.exam.dto.StatsRow stats;
        java.util.List<com.exam.dto.StatsRow> statsRows;
        if (visible == null) {
            statsRows = gradeRepository.stats(examId, courseId, GradeSources.TEACHER, schoolId);
            stats = statsRows.isEmpty() ? null : statsRows.get(0);   // #27 一次取数，避免重复 SQL
        } else if (visible.isEmpty()) {
            stats = null;
        } else {
            statsRows = gradeRepository.statsByClass(examId, courseId, GradeSources.TEACHER, visible, schoolId);
            stats = statsRows.isEmpty() ? null : statsRows.get(0);   // #27 一次取数
        }

        Map<String, Object> result = new HashMap<>();
        List<com.exam.dto.ScoreRow> scores = rows;
        List<StatMath.ScoreItem> items = support.toScoreItems(scores);

        result.put("note", StatQuerySupport.STAT_NOTE);

        if (stats == null || stats.min() == null || items.isEmpty()) {            result.put("total", 0);
            result.put("min", 0);
            result.put("max", 0);
            result.put("avg", 0);
            result.put("percentAvg", 0);
            result.put("totalPercent", 0);
            result.put("stdScore", null);
            result.put("passRate", 0);
            result.put("excellentRate", 0);
            result.put("fullScore", 100);
            result.put("distribution", stat.distribution(new ArrayList<>()));
            return result;
        }

        BigDecimal min = com.exam.dto.StatsRow.toDecimal(stats.min());
        BigDecimal max = com.exam.dto.StatsRow.toDecimal(stats.max());
        BigDecimal avg = com.exam.dto.StatsRow.toDecimal(stats.avg());

        int total = items.size();
        int pass = 0;
        int excellent = 0;
        double percentSum = 0;
        int fullSum = 0, scoreSum = 0;
        for (StatMath.ScoreItem it : items) {
            if (stat.isPass(it)) pass++;
            if (stat.isExcellent(it)) excellent++;
            percentSum += it.percent;
            fullSum += it.fullScore;
            scoreSum += it.score;
        }

        int refFullScore = items.get(0).fullScore; // 参考满分（同场同科一般一致）

        result.put("total", total);
        result.put("min", min);
        result.put("max", max);
        result.put("avg", avg.setScale(2, RoundingMode.HALF_UP));
        result.put("percentAvg", BigDecimal.valueOf(percentSum / total).setScale(2, RoundingMode.HALF_UP));
        // 问题四：总得分率 = Σ得分/Σ满分×100（加权）；标准分 = 各科得分率均值（等权）
        result.put("totalPercent", fullSum > 0
                ? BigDecimal.valueOf(scoreSum * 100.0 / fullSum).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("stdScore", total > 0
                ? BigDecimal.valueOf(percentSum / total).setScale(2, RoundingMode.HALF_UP) : null);
        result.put("passRate", stat.percent(pass, total));
        result.put("excellentRate", stat.percent(excellent, total));
        result.put("fullScore", refFullScore);
        result.put("distribution", stat.distribution(items));
        return result;
    }

    /**
     * 多科总分（单场考试某学生）：返回 totalScore/totalFull/totalPercent/stdScore，并附各科明细。
     * 仅统计 teacher 来源成绩（校内考试口径）；若传入 examCourseGroupId，则仅计入该考试科目组合中 active 的科目。
     * 说明：科目组合按考试归属（exam_course_group 以 (exam_id, course_id) 唯一，组合即某考试 active 科目集合，
     * 无独立组合 id），故此处按 examId 读取该考试的组合配置；第三参数仅作"启用组合过滤"标记，传非空即启用。
     * 家长仅可查已绑定学生；stdScore 按当前标准分权重模式计算（equal/full-weighted/custom-weighted）。
     */
    public Map<String, Object> studentMultiSubject(Long examId, Long studentId, Long examCourseGroupId) {
        Account user = LoginUserContext.requireLogin();
        List<Grade> grades = support.visibleGrades(user, studentId).stream()
                .filter(g -> g.getExamId().equals(examId))
                .collect(java.util.stream.Collectors.toList());

        // 科目组过滤：仅保留该考试组合中 active 的 courseId（组合按 examId 归属）
        Set<Long> activeCourseIds = null;
        if (examCourseGroupId != null) {
            activeCourseIds = examCourseGroupRepository.findByExamId(examId).stream()
                    .filter(ExamCourseGroup::getActive)
                    .map(ExamCourseGroup::getCourseId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        // 一次性加载课程字典
        Set<Long> courseIdSet = new HashSet<>();
        for (Grade g : grades) courseIdSet.add(g.getCourseId());
        Map<Long, com.exam.entity.Course> courseById = new HashMap<>();
        courseRepository.findAllById(courseIdSet).forEach(c -> courseById.put(c.getId(), c));

        int totalScore = 0, totalFull = 0;
        List<StatMath.StdItem> stdItems = new ArrayList<>();
        List<Map<String, Object>> courseItems = new ArrayList<>();
        for (Grade g : grades) {
            if (activeCourseIds != null && !activeCourseIds.contains(g.getCourseId())) continue;
            int full = g.getFullScore() == null || g.getFullScore() <= 0 ? 100 : g.getFullScore();
            double percent = stat.round1(g.getScore() * 100.0 / full);
            // 标准分与总分口径一致：仅计入 teacher 来源（校内考试），避免 parent 来源混入导致双重计数/口径不一致
            if (GradeSources.TEACHER.equals(g.getSource())) {
                totalScore += g.getScore();
                totalFull += full;
                stdItems.add(new StatMath.StdItem(g.getCourseId(), percent, full));
            }
            com.exam.entity.Course course = courseById.get(g.getCourseId());
            Map<String, Object> ci = new HashMap<>();
            ci.put("courseId", g.getCourseId());
            ci.put("courseName", course == null ? "未知课程" : course.getName());
            ci.put("score", g.getScore());
            ci.put("fullScore", full);
            ci.put("percent", percent);
            ci.put("source", g.getSource());
            courseItems.add(ci);
        }
        courseItems.sort(Comparator.comparing(c -> String.valueOf(c.get("courseName"))));

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("examId", examId);
        result.put("examCourseGroupId", examCourseGroupId);
        result.put("totalScore", totalScore);
        result.put("totalFull", totalFull);
        result.put("totalPercent", totalFull > 0 ? stat.round1(totalScore * 100.0 / totalFull) : null);
        result.put("stdScore", stdItems.isEmpty() ? null : stat.weightedStdScore(stdItems));
        result.put("courseCount", courseItems.size());
        result.put("courses", courseItems);
        result.put("note", StatQuerySupport.STAT_NOTE);
        return result;
    }

    // ================= 委托给拆分服务（签名与原类一致，控制器/测试零改动） =================

    /** 委托 {@link ComparisonService#comparison}。 */
    public Map<String, Object> comparison(Long examId, Long courseId, String semester) {
        return comparisonService.comparison(examId, courseId, semester);
    }

    /** 委托 {@link ComparisonService#semesters}。 */
    public List<String> semesters() {
        return comparisonService.semesters();
    }

    /** 委托 {@link RankingService#ranking}。 */
    public Map<String, Object> ranking(Long examId, Long courseId, Boolean classScope, Boolean showTies, String scope) {
        return rankingService.ranking(examId, courseId, classScope, showTies, scope);
    }

    /** 委托 {@link TrendService#progress}。 */
    public Map<String, Object> progress(Long courseId, int limit, boolean regress, String semester) {
        return trendService.progress(courseId, limit, regress, semester);
    }

    /** 委托 {@link TrendService#studentTrend}。 */
    public Map<String, Object> studentTrend(Long studentId) {
        return trendService.studentTrend(studentId);
    }
}
