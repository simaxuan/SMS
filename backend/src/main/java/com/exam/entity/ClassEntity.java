package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 班级。依据《统一数据库字段规范》2.3：新增 levelId/schoolId。
 * 唯一键由 uk_name(single) 调整为 uk_class_school_name(school_id, name)，支持多租户同名校。
 */
@Entity
@Table(name = "class_entity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_class_school_name", columnNames = {"school_id", "name"}),
        indexes = {@Index(name = "idx_class_level", columnList = "level_id")})
@Data
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** 级部归属（FK level.id） */
    @Column(name = "level_id")
    private Long levelId;

    /** 校园归属（FK school.id） */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
