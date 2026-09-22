package com.exam.repository;

import com.exam.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByNameAndSchoolId(String name, Long schoolId);

    /** 按校列出课程（读物字典按校过滤）。 */
    List<Course> findBySchoolId(Long schoolId);

    /** 阶段 B B5：某校是否仍有课程（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
