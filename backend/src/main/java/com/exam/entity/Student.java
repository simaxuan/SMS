package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "student", uniqueConstraints = @UniqueConstraint(columnNames = "student_no"))
@Data
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_no", nullable = false, length = 32)
    private String studentNo;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 8)
    private String gender;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
