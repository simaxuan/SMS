package com.exam.repository;

import com.exam.entity.ParentPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentPreferenceRepository extends JpaRepository<ParentPreference, Long> {

    Optional<ParentPreference> findByParentAccountId(Long parentAccountId);

    /** 删除某家长的偏好（账号删除时清理孤儿偏好，能力预留）。 */
    void deleteByParentAccountId(Long parentAccountId);
}
