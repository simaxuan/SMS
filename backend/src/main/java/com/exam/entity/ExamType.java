package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 考试类型。依据《统一数据库字段规范》2.10：新增 schoolId。
 */
@Entity
@Table(name = "exam_type",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exam_type_school_name", columnNames = {"school_id", "name"}))
@Data
public class ExamType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** 校园归属 */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
