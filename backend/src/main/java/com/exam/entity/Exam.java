package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam", indexes = {
        @Index(name = "idx_exam_source_semester", columnList = "source, semester"),
        @Index(name = "idx_exam_school_source", columnList = "school_id, source")})
@Data
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "exam_type_id")
    private Long examTypeId;

    @Column(name = "exam_date")
    private LocalDate examDate;

    /** 学期（如 2026春/2026秋），用于按学期维度统计对比 */
    @Column(length = 32)
    private String semester;

    /** 考试归属：school=校内考试（老师管理），custom=家长自定义小测 */
    @Column(nullable = false, length = 16)
    private String source = "school";

    /** 自定义小测的创建家长账号（仅 custom 来源使用） */
    @Column(name = "creator_account_id")
    private Long creatorAccountId;

    /** 校园归属 */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
