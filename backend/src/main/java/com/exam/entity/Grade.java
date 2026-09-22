package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_exam_course_source",
                columnNames = {"student_id", "exam_id", "course_id", "source"}),
        indexes = {
                @Index(name = "idx_grade_exam_course_source", columnList = "exam_id, course_id, source"),
                @Index(name = "idx_grade_course_source", columnList = "course_id, source"),
                @Index(name = "idx_grade_student_source", columnList = "student_id, source"),
                // 八轮强约束：冗余 school_id（写路径快照回填），支撑按校过滤/未来 DB 外键/报表
                @Index(name = "idx_grade_school", columnList = "school_id")
        })
@Data
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 乐观锁版本（并发更新防护：读取→修改→save 时若版本不一致抛 OptimisticLockException，避免最后写入覆盖）。 */
    @Version
    private Long version;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 校园归属冗余快照（写路径依所属学生回填；自建学生成绩可空）。八轮强约束新增，辅助多租户隔离与查询。 */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Integer score;

    /** 本次考试该课程满分（允许不同考试/不同课程不同，如语文 100 或 120）；基线要求 NOT NULL DEFAULT 100 */
    @Column(name = "full_score", nullable = false, columnDefinition = "int default 100")
    private Integer fullScore = 100;

    /** 成绩来源：teacher=老师录入，parent=家长录入 */
    @Column(nullable = false, length = 16)
    private String source = "teacher";

    /** 录入人账号（家长来源必填；老师来源=录入老师账号） */
    @Column(name = "creator_account_id")
    private Long creatorAccountId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
