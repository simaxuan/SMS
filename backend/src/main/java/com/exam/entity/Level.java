package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 级部/年级（独立表）。依据《统一数据库字段规范》2.2。
 */
@Entity
@Table(name = "level",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_school_level", columnNames = {"school_id", "name"}))
@Data
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    /** 级部名，如"三年级"/"初二" */
    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
