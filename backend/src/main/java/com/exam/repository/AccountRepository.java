package com.exam.repository;

import com.exam.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    Optional<Account> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    /** 按角色查账号列表（如组织管理中列老师账号用于班级归属绑定）。 */
    List<Account> findByRole(String role);

    /** 按角色 + 归属校查账号列表（多租户：老师管理按校隔离）。 */
    List<Account> findByRoleAndSchoolId(String role, Long schoolId);

    /** 阶段 B B5：某校是否仍有账号（级联删除安全网）。 */
    boolean existsBySchoolId(Long schoolId);
}
