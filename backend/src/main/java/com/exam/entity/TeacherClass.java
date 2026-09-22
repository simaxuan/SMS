package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 老师-班级归属（一人多班、一班可多人，天然支持跨年级）。依据《统一数据库字段规范》2.4。
 */
@Entity
@Table(name = "teacher_class",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_teacher_class", columnNames = {"teacher_account_id", "class_id"}),
        indexes = {@Index(name = "idx_tc_class", columnList = "class_id")})
@Data
public class TeacherClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_account_id", nullable = false)
    private Long teacherAccountId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
