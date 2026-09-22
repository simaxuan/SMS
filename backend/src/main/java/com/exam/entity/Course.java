package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 课程。依据《统一数据库字段规范》2.10：新增 schoolId。
 */
@Entity
@Table(name = "course",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_school_name", columnNames = {"school_id", "name"}))
@Data
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "full_score")
    private Integer fullScore = 100;

    /** 校园归属 */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
