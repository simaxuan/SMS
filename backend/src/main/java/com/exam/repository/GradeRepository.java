package com.exam.repository;

import com.exam.dto.CourseRow;
import com.exam.dto.RankRow;
import com.exam.dto.ScoreRow;
import com.exam.dto.StatsRow;
import com.exam.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    boolean existsByStudentIdAndExamIdAndCourseIdAndSource(
            Long studentId, Long examId, Long courseId, String source);

    boolean existsByStudentIdAndExamIdAndCourseIdAndSourceAndIdNot(
            Long studentId, Long examId, Long courseId, String source, Long id);

    boolean existsByCourseId(Long courseId);

    boolean existsByExamId(Long examId);

    // ---------- 老师：按来源过滤（teacher） ----------

    List<Grade> findByExamIdAndCourseIdAndSource(Long examId, Long courseId, String source);

    // ---------- 阶段 B：全链路 school 兜底中央切面 ----------
    // #8 统一过滤基准为 s.schoolId（join Student，与 stats/scores/rankRows/courseRowsByExam 一致），
    // 杜绝「search 用 g.schoolId 快照、统计用 s.schoolId」两套口径互相包含/丢失的问题。
    // grade.school_id 冗余快照仍回填，仅作索引/运维兜底，不再作为 auth 过滤基准。
    //   当前登录 schoolId 非空（SCOPE_ALL/超管）→ 按学生归属校过滤，杜绝跨校失真；
    //   schoolId 为空（全局超管）→ 放开全量（向后兼容）。

    @Query("""
            select g from Grade g
            join com.exam.entity.Student s on s.id = g.studentId
            where g.source = :source
              and (:examId is null or g.examId = :examId)
              and (:courseId is null or g.courseId = :courseId)
              and (:studentId is null or g.studentId = :studentId)
              and (:schoolId is null or s.schoolId = :schoolId)
            order by g.id
            """)
    List<Grade> search(@Param("source") String source,
                       @Param("examId") Long examId,
                       @Param("courseId") Long courseId,
                       @Param("studentId") Long studentId,
                       @Param("schoolId") Long schoolId);

    @Query("""
            select new com.exam.dto.StatsRow(min(g.score), max(g.score), avg(g.score))
            from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.examId = :examId and g.courseId = :courseId and g.source = :source
              and (:schoolId is null or s.schoolId = :schoolId)
            """)
    List<StatsRow> stats(@Param("examId") Long examId,
                         @Param("courseId") Long courseId,
                         @Param("source") String source,
                         @Param("schoolId") Long schoolId);

    @Query("""
            select new com.exam.dto.ScoreRow(g.score, g.fullScore) from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.examId = :examId and g.courseId = :courseId and g.source = :source
              and (:schoolId is null or s.schoolId = :schoolId)
            """)
    List<ScoreRow> scores(@Param("examId") Long examId,
                          @Param("courseId") Long courseId,
                          @Param("source") String source,
                          @Param("schoolId") Long schoolId);

    /** 老师读路径班域：仅统计可见班级（classIds）内的成绩聚合（班域已属本校，schoolId 作全校兜底）。 */
    @Query("""
            select new com.exam.dto.StatsRow(min(g.score), max(g.score), avg(g.score))
            from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.examId = :examId and g.courseId = :courseId and g.source = :source
              and s.classId in :classIds
              and (:schoolId is null or s.schoolId = :schoolId)
            """)
    List<StatsRow> statsByClass(@Param("examId") Long examId,
                                @Param("courseId") Long courseId,
                                @Param("source") String source,
                                @Param("classIds") java.util.Collection<Long> classIds,
                                @Param("schoolId") Long schoolId);

    /** 老师读路径班域：仅取可见班级（classIds）内的成绩明细（分数/满分，用于分布）。 */
    @Query("""
            select new com.exam.dto.ScoreRow(g.score, g.fullScore) from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.examId = :examId and g.courseId = :courseId and g.source = :source
              and s.classId in :classIds
              and (:schoolId is null or s.schoolId = :schoolId)
            """)
    List<ScoreRow> scoresByClass(@Param("examId") Long examId,
                                 @Param("courseId") Long courseId,
                                 @Param("source") String source,
                                 @Param("classIds") java.util.Collection<Long> classIds,
                                 @Param("schoolId") Long schoolId);

    // ---------- 家长：绑定学生范围 ----------

    List<Grade> findByStudentId(Long studentId);

    /** I-6：按学生集合+来源一次下压 SQL（替代在内存里 filter source，避免全量拉取）。 */
    List<Grade> findByStudentIdInAndSource(List<Long> studentIds, String source);

    List<Grade> findByStudentIdIn(List<Long> studentIds);

    List<Grade> findByExamId(Long examId);

    /** 八轮强约束：按校归属过滤读取（行级隔离兜底），供报表/运维/未来外键场景按整校拉取。 */
    List<Grade> findBySchoolId(Long schoolId);

    /** 阶段 B B5：某校是否仍有成绩（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);

    // ---------- 排名 / 对比 / 进步分析 ----------

    /** 单科成绩明细（含学生信息与班级），用于排名与班级对比。schoolId 空=全局放开。 */
    @Query("""
            select new com.exam.dto.RankRow(g.studentId, s.studentNo, s.name, s.classId, g.score, g.fullScore)
            from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.examId = :examId and g.courseId = :courseId and g.source = :source
              and (:schoolId is null or s.schoolId = :schoolId)
            """)
    List<RankRow> rankRows(@Param("examId") Long examId,
                           @Param("courseId") Long courseId,
                           @Param("source") String source,
                           @Param("schoolId") Long schoolId);

    /** P1-3 SQL 下压：按可见班级集合直接取单科成绩明细（替代原先取全量后内存过滤，避免全表/全校拉取）。 */
    @Query("""
            select new com.exam.dto.RankRow(g.studentId, s.studentNo, s.name, s.classId, g.score, g.fullScore)
            from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.examId = :examId and g.courseId = :courseId and g.source = :source
              and s.classId in :classIds
              and (:schoolId is null or s.schoolId = :schoolId)
            """)
    List<RankRow> rankRowsByClassIds(@Param("examId") Long examId,
                                     @Param("courseId") Long courseId,
                                     @Param("source") String source,
                                     @Param("classIds") java.util.Collection<Long> classIds,
                                     @Param("schoolId") Long schoolId);

    /** 某课程在指定考试集合下、按学生的得分率（用于跨考试对比/进步分析）。schoolId 空=全局放开。 */
    @Query("""
            select new com.exam.dto.CourseRow(g.examId, g.studentId, s.classId, g.score, g.fullScore)
            from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.courseId = :courseId and g.source = :source
              and (:schoolId is null or s.schoolId = :schoolId)
            order by g.examId
            """)
    List<CourseRow> courseRowsByExam(@Param("courseId") Long courseId,
                                     @Param("source") String source,
                                     @Param("schoolId") Long schoolId);

    /** P1-3 SQL 下压：按可见班级集合直接取某课程跨考试得分率行（进步/学期对比不再拉全校后内存过滤）。 */
    @Query("""
            select new com.exam.dto.CourseRow(g.examId, g.studentId, s.classId, g.score, g.fullScore)
            from Grade g, com.exam.entity.Student s
            where s.id = g.studentId and s.deleted = false
              and g.courseId = :courseId and g.source = :source
              and s.classId in :classIds
              and (:schoolId is null or s.schoolId = :schoolId)
            order by g.examId
            """)
    List<CourseRow> courseRowsByExamByClassIds(@Param("courseId") Long courseId,
                                               @Param("source") String source,
                                               @Param("classIds") java.util.Collection<Long> classIds,
                                               @Param("schoolId") Long schoolId);
}
