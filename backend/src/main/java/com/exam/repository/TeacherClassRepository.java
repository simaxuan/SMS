package com.exam.repository;

import com.exam.entity.TeacherClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeacherClassRepository extends JpaRepository<TeacherClass, Long> {

    List<TeacherClass> findByTeacherAccountId(Long teacherAccountId);

    List<TeacherClass> findByClassIdIn(List<Long> classIds);

    boolean existsByTeacherAccountIdAndClassId(Long teacherAccountId, Long classId);

    /** 删除某老师全部班级归属（删除教师账号时联动清理）。 */
    void deleteByTeacherAccountId(Long teacherAccountId);

    /** 删除某班级的全部老师归属（删除班级时联动清理，防 teacher_class 悬空）。 */
    void deleteByClassId(Long classId);
}
