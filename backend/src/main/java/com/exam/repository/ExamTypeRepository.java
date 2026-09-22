package com.exam.repository;

import com.exam.entity.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamTypeRepository extends JpaRepository<ExamType, Long> {

    boolean existsByName(String name);

    /** 复合唯一键 (school_id, name) 对应的按校查重。 */
    boolean existsBySchoolIdAndName(Long schoolId, String name);

    List<ExamType> findBySchoolId(Long schoolId);

    /** 阶段 B B5：某校是否仍有考试类型（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
