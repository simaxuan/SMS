package com.exam.repository;

import com.exam.entity.ExamCourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamCourseGroupRepository extends JpaRepository<ExamCourseGroup, Long> {

    List<ExamCourseGroup> findByExamId(Long examId);

    void deleteByExamId(Long examId);

    /** 某校是否仍有考试-科目组合引用于课程/考试（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
