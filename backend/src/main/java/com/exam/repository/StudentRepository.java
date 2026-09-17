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

    Optional<Student> findByStudentNo(String studentNo);

    Optional<Student> findByIdAndDeletedFalse(Long id);

    boolean existsByStudentNo(String studentNo);

    boolean existsByStudentNoAndIdNot(String studentNo, Long id);

    @Query("""
            select s from Student s
            where s.deleted = false
              and (:keyword is null or :keyword = ''
                   or lower(s.studentNo) like lower(concat('%', :keyword, '%'))
                   or lower(s.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Student> search(@Param("keyword") String keyword, Pageable pageable);

    @Modifying
    @Query("update Student s set s.deleted = true where s.id = :id and s.deleted = false")
    int softDelete(@Param("id") Long id);
}
