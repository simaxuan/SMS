package com.exam.repository;

import com.exam.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentNoAndNameAndDeletedFalse(String studentNo, String name);

    Optional<Student> findByIdAndDeletedFalse(Long id);

    boolean existsByStudentNo(String studentNo);

    boolean existsByStudentNoAndIdNot(String studentNo, Long id);

    /** 班级下是否仍存在（未删除）学生，用于删除班级前的引用校验 */
    boolean existsByClassIdAndDeletedFalse(Long classId);

    /** 阶段 B B5：某校是否仍有未删除学生（级联删除安全网）。 */
    boolean existsBySchoolIdAndDeletedFalse(Long schoolId);

    /** 按班级集合查询未删除学生（用于老师读路径按可见班级过滤成绩）。 */
    java.util.List<Student> findByClassIdInAndDeletedFalse(java.util.Collection<Long> classIds);

    /** 按 ID 集合一次性批量查询未删除学生（P1-2：替代逐条 findById，消除 N+1）。
     * 注意：因 Student 含 classId/schoolId 等多 Long 外键字段，方法名派生会误判 studentId 为嵌套属性，
     * 故用显式 @Query（id in 集合 + deleted=false）。 */
    @Query("select s from Student s where s.id in :ids and s.deleted = false")
    java.util.List<Student> findStudentsByIdsAndNotDeleted(@Param("ids") java.util.Collection<Long> ids);

    @Query("""
            select s from Student s
            where s.deleted = false
              and (:schoolId is null or s.schoolId = :schoolId)
              and (:keyword is null or :keyword = ''
                   or lower(s.studentNo) like lower(concat('%', :keyword, '%'))
                   or lower(s.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Student> search(@Param("schoolId") Long schoolId,
                         @Param("keyword") String keyword, Pageable pageable);

    @Modifying
    @Query("update Student s set s.deleted = true where s.id = :id and s.deleted = false")
    int softDelete(@Param("id") Long id);
}
