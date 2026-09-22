package com.exam.repository;

import com.exam.entity.ParentStudentBind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParentStudentBindRepository extends JpaRepository<ParentStudentBind, Long> {

    List<ParentStudentBind> findByParentAccountId(Long parentAccountId);

    boolean existsByParentAccountIdAndStudentId(Long parentAccountId, Long studentId);

    void deleteByParentAccountIdAndStudentId(Long parentAccountId, Long studentId);

    /** 删除某学生的全部绑定（软删学生时清理孤儿绑定）。 */
    void deleteByStudentId(Long studentId);

    /** 删除某家长的全部绑定（账号删除时清理孤儿绑定，能力预留）。 */
    void deleteByParentAccountId(Long parentAccountId);
}
