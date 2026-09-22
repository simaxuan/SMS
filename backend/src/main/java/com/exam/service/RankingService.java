package com.exam.service;

import com.exam.entity.Account;
import com.exam.common.GradeSources;
import com.exam.common.LoginUserContext;
import com.exam.entity.ParentPreference;
import com.exam.entity.Student;
import com.exam.repository.CourseRepository;
import com.exam.repository.GradeRepository;
import com.exam.repository.LevelRepository;
import com.exam.repository.ParentPreferenceRepository;
import com.exam.repository.ParentStudentBindRepository;
import com.exam.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 班级/单科排名职责服务。
 * <p>老师：返回该场次该科目全部 teacher 来源成绩的名次（含并列），范围受 scope 与 {@link DataScopeService} 班级归属约束。
 * 家长：受「家长端排名可见开关」与隐私档位 parent_rank_detail 控制（默认 aggregate：本人+班级聚合，无他人明细）。
 *
 * <p>由 {@link StatisticsService} 门面类拆分而来（原 {@code StatisticsService#ranking}），逻辑逐字保留。
 */
@Service
public class RankingService {

    private final GradeRepository gradeRepository;
    private final CourseRepository courseRepository;
    private final LevelRepository levelRepository;
    private final DataScopeService dataScopeService;
    private final SettingsService settingsService;
    private final ParentPreferenceRepository preferenceRepository;
    private final ParentStudentBindRepository bindRepository;
    private final StudentRepository studentRepository;
    private final StatQuerySupport support;
    private final StatMath stat;

    public RankingService(GradeRepository gradeRepository,
                          CourseRepository courseRepository,
                          LevelRepository levelRepository,
                          DataScopeService dataScopeService,
                          SettingsService settingsService,
                          ParentPreferenceRepository preferenceRepository,
                          ParentStudentBindRepository bindRepository,
                          StudentRepository studentRepository,
                          StatQuerySupport support,
                          StatMath stat) {
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.levelRepository = levelRepository;
        this.dataScopeService = dataScopeService;
        this.settingsService = settingsService;
        this.preferenceRepository = preferenceRepository;
        this.bindRepository = bindRepository;
        this.studentRepository = studentRepository;
        this.support = support;
        this.stat = stat;
    }

    /**
     * 班级/单科排名。
     *
     * @param classScope 只看孩子所在班级排名（true=班内排名；null=家长用偏好，老师忽略）
     * @param showTies   是否显示并列
     * @param scope      排名范围：class/level/school（仅老师用；默认 class）
     */
    public Map<String, Object> ranking(Long examId, Long courseId, Boolean classScope, Boolean showTies, String scope) {
        com.exam.entity.Account user = LoginUserContext.requireLogin();
        String source = GradeSources.TEACHER;
        // 阶段 B B1：SQL 层 school 兜底——老师按学校/班级收敛，家长(schoolId 多为 null)放开再按绑定过滤
        Long schoolFilter = dataScopeService.schoolFilterForStatistics(user);
        // I-3 班域：CLASS 老师仅统计可见班级。P1-3 由「全量 rankRows + 内存过滤」改为 SQL 下压（rankRowsByClassIds）。
        java.util.Set<Long> visible = dataScopeService.visibleClassIds(user);
        List<com.exam.dto.RankRow> rows;
        if (visible != null && !visible.isEmpty()) {
            rows = gradeRepository.rankRowsByClassIds(examId, courseId, source, visible, null);
        } else {
            rows = gradeRepository.rankRows(examId, courseId, source, schoolFilter);
        }

        // 组装行（原始分数/满分/班级）并按分数降序
        List<Map<String, Object>> ranked = buildRankedRows(rows);

        Map<String, Object> result = new HashMap<>();
        result.put("examId", examId);
        result.put("courseId", courseId);
        result.put("courseName", courseRepository.findById(courseId).map(com.exam.entity.Course::getName).orElse("未知课程"));
        result.put("scope", scope == null ? "class" : scope);

        // ================= 老师：归属过滤 + scope 三级分组排名 =================
        if (com.exam.entity.Account.ROLE_TEACHER.equals(user.getRole())) {
            // 归属过滤已在 SQL 下压完成（可见班级集合），此处分组排名；SCOPE_ALL 无班级收敛（visible==null）放开全校
            boolean ties = showTies == null ? true : showTies;
            ranked = applyScopeRanking(ranked, scope, ties);
            result.put("rows", ranked);
            result.put("total", ranked.size());
            result.put("classScope", false);
            result.put("showTies", ties);
            result.put("note", "排名基于" + source + "来源成绩，按分数降序" + (ties ? "（含并列名次）。" : "（并列唯一顺延）。"));
            return result;
        }

        // ================= 家长：受排名开关 + 隐私档位控制 =================
        if (!settingsService.getBool(SettingsService.KEY_PARENT_RANK_VISIBLE, true)) {
            result.put("rows", new ArrayList<>());
            result.put("total", 0);
            result.put("disabled", true);
            return result;
        }
        // P0 修复：家长查询必须按绑定学生的归属校过滤成绩，确保排名名次为同校名次、
        // 越校数据不参与计算（此前 schoolFilterForStatistics 对家长返回 null，导致拉取跨校成绩、
        // 赋的是跨校全局名次，名次语义错误且越校数据混入）。
        Long parentSchoolId = resolveParentSchoolId(user.getId());
        if (parentSchoolId != null) {
            List<com.exam.dto.RankRow> pRows = gradeRepository.rankRows(examId, courseId, source, parentSchoolId);
            ranked = buildRankedRows(pRows);
        }
        ParentPreference pref =
                preferenceRepository.findByParentAccountId(user.getId()).orElse(null);
        boolean sc = classScope != null ? classScope : (pref != null && pref.isClassScope());
        boolean ties = showTies != null ? showTies : (pref == null || pref.isShowTies());

        List<Long> bound = bindRepository.findByParentAccountId(user.getId()).stream()
                .map(com.exam.entity.ParentStudentBind::getStudentId)
                .collect(java.util.stream.Collectors.toList());
        if (bound.isEmpty()) {
            result.put("rows", new ArrayList<>());
            result.put("total", 0);
            result.put("disabled", false);
            return result;
        }

        // 班内/级部排名：为绑定学生所在范围保留该范围全部学生并重算名次
        List<Map<String, Object>> mine;
        if (sc) {
            List<Long> childClassIds = new ArrayList<>();
            for (Map<String, Object> m : ranked) {
                if (bound.contains(stat.toLong(m.get("studentId"))) && m.get("classId") != null
                        && !childClassIds.contains(stat.toLong(m.get("classId")))) {
                    childClassIds.add(stat.toLong(m.get("classId")));
                }
            }
            mine = new ArrayList<>();
            for (Map<String, Object> m : ranked) {
                if (childClassIds.contains(m.get("classId"))) {
                    mine.add(m);
                }
            }
            stat.assignRank(mine, ties);
        } else {
            // 全校范围：先在完整 ranked 上赋真实全校名次，再仅过滤绑定学生用于展示
            stat.assignRank(ranked, ties);
            mine = ranked.stream().filter(r -> bound.contains(stat.toLong(r.get("studentId"))))
                    .collect(java.util.stream.Collectors.toList());
        }

        // 问题一：隐私档位（默认 aggregate）。档位决定返回粒度。
        String detail = settingsService.getString(SettingsService.KEY_PARENT_RANK_DETAIL, "aggregate");
        result.put("classScope", sc);
        result.put("showTies", ties);
        result.put("rankDetail", detail);
        if ("full".equals(detail)) {
            // 完整范围（仅明确授权）：返回范围内全部行
            result.put("rows", mine);
            result.put("total", mine.size());
            result.put("note", "排名基于" + source + "来源成绩，范围明细模式。");
            return result;
        }
        if ("limited".equals(detail)) {
            // 最严：仅本人
            List<Map<String, Object>> selfList = new ArrayList<>();
            for (Map<String, Object> m : mine) {
                if (bound.contains(stat.toLong(m.get("studentId")))) selfList.add(m);
            }
            result.put("rows", selfList);
            result.put("total", selfList.size());   // 行数语义与 rows 一致
            result.put("rangeTotal", mine.size());  // #28 范围总人数（分母）单列，避免误导
            result.put("note", "出于隐私保护，仅展示本人名次。");
            return result;
        }
        // aggregate（默认）：本人 + 班级/级部聚合（不返回他人明细）
        List<Map<String, Object>> selfList = new ArrayList<>();
        for (Map<String, Object> m : mine) {
            if (bound.contains(stat.toLong(m.get("studentId")))) selfList.add(m);
        }
        result.put("rows", selfList);
        result.put("total", selfList.size());      // 行数语义与 rows 一致
        result.put("rangeTotal", mine.size());     // #28 范围总人数（分母）单列，避免误导
        result.put("classAgg", support.classAggregate(mine));
        result.put("note", "出于隐私保护，仅展示本人名次与班级整体分布，不展示他人明细。");
        return result;
    }

    /**
     * 将 rankRows 原始查询结果组装为排名行（分数/满分/班级）并按分数降序排列。
     * 抽为独立方法，便于家长路径按校重新查询后复用同一套组装逻辑（避免重复与漂移）。
     */
    private List<Map<String, Object>> buildRankedRows(List<com.exam.dto.RankRow> rows) {
        List<Map<String, Object>> ranked = new ArrayList<>();
        List<Long> classIds = rows.stream().map(com.exam.dto.RankRow::classId)
                .filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, String> classNames = support.classNameByIds(classIds);
        for (com.exam.dto.RankRow r : rows) {
            int score = r.score() == null ? 0 : r.score();
            int full = r.safeFullScore();
            Long cid = r.classId();
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", r.studentId());
            m.put("studentNo", support.str(r.studentNo()));
            m.put("name", support.str(r.name()));
            m.put("classId", cid);
            m.put("score", score);
            m.put("fullScore", full);
            m.put("percent", stat.round1(score * 100.0 / full));
            m.put("className", cid == null ? "未分班" : classNames.getOrDefault(cid, "班级" + cid));
            ranked.add(m);
        }
        Comparator<Map<String, Object>> byScoreDesc =
                Comparator.comparingInt((Map<String, Object> x) -> ((Number) x.get("score")).intValue()).reversed()
                        .thenComparing(x -> String.valueOf(x.get("name")));
        ranked.sort(byScoreDesc);
        return ranked;
    }

    /**
     * 解析家长应过滤的成绩归属校：取该家长绑定学生的 schoolId。
     * 正常家长绑定同校学生 → 返回该 schoolId（确保排名为本校名次）；
     * 若绑定学生跨校（异常）或均无归属校 → 返回 null（保守放开，保持原行为，不丢数据）。
     * P1-2 性能优化：原先逐绑定 findById 触发 N+1；现一次性批量按 id 取学生（findByStudentIdInAndDeletedFalse），
     * 再按 schoolId 分组解析，减少数据库往返。语义完全不变。
     */
    private Long resolveParentSchoolId(Long parentAccountId) {
        java.util.List<com.exam.entity.ParentStudentBind> binds =
                bindRepository.findByParentAccountId(parentAccountId);
        if (binds == null || binds.isEmpty()) return null;
        // 批量取绑定学生（一次查询），避免逐绑定 N+1
        List<Long> studentIds = binds.stream()
                .map(com.exam.entity.ParentStudentBind::getStudentId)
                .distinct().collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, Student> stuById = studentRepository
                .findStudentsByIdsAndNotDeleted(studentIds).stream()
                .collect(java.util.stream.Collectors.toMap(Student::getId, s -> s));
        Long school = null;
        boolean mixed = false;
        for (com.exam.entity.ParentStudentBind b : binds) {
            Student st = stuById.get(b.getStudentId());
            if (st == null || st.getSchoolId() == null) continue;
            if (school == null) school = st.getSchoolId();
            else if (!school.equals(st.getSchoolId())) mixed = true;
        }
        return mixed ? null : school;
    }

    /**
     * 按 scope 对排名行分组重排（老师端三级范围）：入参 rows 已按分数降序。
     * <ul>
     *   <li>school：全量整体排名，scopeGroup=全校</li>
     *   <li>level：按 classId→levelId 分组，组内排名，scopeGroup=级部名</li>
     *   <li>class（默认）：按 classId 分组，组内排名，scopeGroup=班级名</li>
     * </ul>
     * 返回连续分组块（组内保持分数降序），并被赋上 scopeGroup 标签。
     */
    private List<Map<String, Object>> applyScopeRanking(List<Map<String, Object>> rows, String scope, boolean ties) {
        String sc = scope == null ? "class" : scope;
        for (Map<String, Object> r : rows) r.remove("rank");

        List<Long> classIds = rows.stream()
                .map(r -> r.get("classId") == null ? null : ((Number) r.get("classId")).longValue())
                .filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.toList());

        if ("school".equals(sc)) {
            stat.assignRank(rows, ties);
            for (Map<String, Object> r : rows) r.put("scopeGroup", "全校");
            return rows;
        }

        Map<Long, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        if ("level".equals(sc)) {
            Map<Long, Long> classLevel = support.classLevelByIds(classIds);
            Map<Long, String> levelNames = new HashMap<>();
            if (!classLevel.isEmpty()) {
                List<Long> lids = classLevel.values().stream().filter(java.util.Objects::nonNull).distinct()
                        .collect(java.util.stream.Collectors.toList());
                levelRepository.findAllById(lids).forEach(l -> levelNames.put(l.getId(), l.getName()));
            }
            for (Map<String, Object> r : rows) {
                Long cid = r.get("classId") == null ? null : ((Number) r.get("classId")).longValue();
                Long lid = cid == null ? null : classLevel.get(cid);
                groups.computeIfAbsent(lid, k -> new ArrayList<>()).add(r);
            }
            for (Map.Entry<Long, List<Map<String, Object>>> e : groups.entrySet()) {
                stat.assignRank(e.getValue(), ties);
                String label = e.getKey() == null ? "未分班" : levelNames.getOrDefault(e.getKey(), "级部" + e.getKey());
                for (Map<String, Object> r : e.getValue()) r.put("scopeGroup", label);
            }
        } else { // class（默认）
            for (Map<String, Object> r : rows) {
                Long cid = r.get("classId") == null ? null : ((Number) r.get("classId")).longValue();
                groups.computeIfAbsent(cid, k -> new ArrayList<>()).add(r);
            }
            for (Map.Entry<Long, List<Map<String, Object>>> e : groups.entrySet()) {
                stat.assignRank(e.getValue(), ties);
                String label = e.getKey() == null ? "未分班" : String.valueOf(e.getValue().get(0).get("className"));
                for (Map<String, Object> r : e.getValue()) r.put("scopeGroup", label);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<Map<String, Object>> g : groups.values()) out.addAll(g);
        return out;
    }
}
