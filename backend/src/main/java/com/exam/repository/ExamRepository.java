package com.exam.repository;

import com.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findAllBySourceOrderByIdAsc(String source);

    /** 按校 + 来源列考试（读路径 school 下推）。 */
    List<Exam> findBySchoolIdAndSourceOrderByIdAsc(Long schoolId, String source);

    /**
     * 按「来源 + 学期」列考试；schoolId 非 null 时叠加本校隔离（多租户），schoolId 为 null 放开（兼容历史/超管）。
     * 返回值用于学期维度统计时锁定本校学期内的考试集合，杜绝跨校考试 ID 混入。
     */
    @Query("select e from Exam e where e.source = :source"
            + " and (:schoolId is null or e.schoolId = :schoolId)"
            + " and (:semester is null or e.semester = :semester) order by e.id asc")
    List<Exam> findBySourceAndSemester(@Param("source") String source,
                                       @Param("semester") String semester,
                                       @Param("schoolId") Long schoolId);

    /** 全部非空学期（school 来源），用于学期维度下拉。 */
    @Query("select distinct e.semester from Exam e where e.source = :source and e.semester is not null and e.semester <> '' order by e.semester asc")
    List<String> findDistinctSemesters(@Param("source") String source);

    /** #29 按校过滤的非空学期（school 来源）：学期下拉按登录校隔离，防跨校信息泄露。 */
    @Query("select distinct e.semester from Exam e where e.source = :source and e.schoolId = :schoolId and e.semester is not null and e.semester <> '' order by e.semester asc")
    List<String> findDistinctSemestersBySchool(@Param("source") String source, @Param("schoolId") Long schoolId);

    boolean existsByExamTypeId(Long examTypeId);

    Optional<Exam> findBySourceAndCreatorAccountIdAndName(String source, Long creatorAccountId, String name);

    List<Exam> findAllBySourceAndCreatorAccountId(String source, Long creatorAccountId);

    /** 阶段 B B5：某校是否仍有考试（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
