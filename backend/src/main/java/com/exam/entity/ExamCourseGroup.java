package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 考试-科目组合（多科总分/标准分参与科目配置）。依据《统一数据库字段规范》2.12。
 */
@Entity
@Table(name = "exam_course_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exam_course", columnNames = {"exam_id", "course_id"}))
@Data
public class ExamCourseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 是否计入总分/标准分（默认 true） */
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
