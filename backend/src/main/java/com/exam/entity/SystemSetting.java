package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统设置（键值对）。由老师端管理与维护。
 * 当前支持的键：
 *  - parent_rank_visible : 家长端是否展示孩子排名（"true"/"false"），默认 true
 */
@Entity
@Table(name = "system_setting", uniqueConstraints = @UniqueConstraint(
        name = "uk_setting_school_key", columnNames = {"school_id", "skey"}))
@Data
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 校园归属（阶段 B S2 按校隔离）；null=全局默认（家长端读全局）。 */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "skey", nullable = false, length = 64)
    private String key;

    /** 设置值。注：列名用 svalue 以避开 H2/PostgreSQL 保留字 value。 */
    @Column(name = "svalue", nullable = false, length = 256)
    private String value;

    @Column(length = 128)
    private String remark;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
