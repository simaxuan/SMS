package com.exam.repository;

import com.exam.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    /** 全局默认设置（school_id IS NULL）。 */
    Optional<SystemSetting> findBySchoolIdIsNullAndKey(String key);

    /** 某校设置（school_id = :schoolId）。 */
    Optional<SystemSetting> findBySchoolIdAndKey(Long schoolId, String key);

    /** 兼容旧派生方法签名（保留，供历史代码/测试）。 */
    Optional<SystemSetting> findByKey(String key);

    /** 某校全部设置集合（school_id=:schoolId；null 返回全局）。 */
    List<SystemSetting> findBySchoolId(Long schoolId);

    List<SystemSetting> findBySchoolIdIsNull();

    /** 某校是否仍有系统设置（级联删除安全网，删除学校前校验）。 */
    boolean existsBySchoolId(Long schoolId);

    /** 删除某校全部设置（school_id 非空时；删除学校级联清理）。 */
    void deleteBySchoolId(Long schoolId);
}
