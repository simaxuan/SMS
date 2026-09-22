package com.exam.repository;

import com.exam.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LevelRepository extends JpaRepository<Level, Long> {

    List<Level> findBySchoolId(Long schoolId);

    boolean existsBySchoolIdAndName(Long schoolId, String name);

    /** 阶段 B B5：某校是否仍有级部（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
