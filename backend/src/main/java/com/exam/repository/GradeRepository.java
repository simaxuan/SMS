package com.exam.repository;

import com.exam.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    boolean existsByStudentIdAndExamIdAndCourseId(Long studentId, Long examId, Long courseId);

    boolean existsByStudentIdAndExamIdAndCourseIdAndIdNot(
            Long studentId, Long examId, Long courseId, Long id);

    Optional<Grade> findByStudentIdAndExamIdAndCourseId(
            Long studentId, Long examId, Long courseId);

    List<Grade> findByExamIdAndCourseId(Long examId, Long courseId);

    @Query("""
            select min(g.score), max(g.score), avg(g.score)
            from Grade g
            where g.examId = :examId and g.courseId = :courseId
            """)
    List<Object[]> stats(@Param("examId") Long examId, @Param("courseId") Long courseId);

    @Query("""
            select g.studentId, g.score from Grade g
            where g.examId = :examId and g.courseId = :courseId
            """)
    List<Object[]> scores(@Param("examId") Long examId, @Param("courseId") Long courseId);
}
