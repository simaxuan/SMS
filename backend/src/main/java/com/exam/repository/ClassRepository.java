package com.exam.repository;

import com.exam.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndSchoolId(String name, Long schoolId);

    /** 按校列出全部班级（SCOPE_SCHOOL 可见集合、读物字典按校过滤）。 */
    List<ClassEntity> findBySchoolId(Long schoolId);

    /** 阶段 B B5：级联安全——某级部是否仍有班级引用（删除级部前校验）。 */
    boolean existsByLevelId(Long levelId);

    /** 阶段 B B5：某校是否仍有班级（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
