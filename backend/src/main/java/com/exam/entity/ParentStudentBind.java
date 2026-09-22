package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "parent_student_bind",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_parent_student",
                columnNames = {"parent_account_id", "student_id"}))
@Data
public class ParentStudentBind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_account_id", nullable = false)
    private Long parentAccountId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(length = 16)
    private String relation;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
