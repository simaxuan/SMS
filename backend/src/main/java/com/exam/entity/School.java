package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 校园（多租户根）。依据《统一数据库字段规范》2.1。
 */
@Entity
@Table(name = "school", uniqueConstraints = @UniqueConstraint(
        name = "uk_school_code", columnNames = "code"))
@Data
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    /** 学校编码，多校隔离/认领用 */
    @Column(length = 32)
    private String code;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
