package com.exam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生。依据《统一数据库字段规范》2.6。
 * 新增：idCard(AES)、idCardHash、fatherPhone、motherPhone、origin、parentOwnerAccountId、enrolled、merged、schoolId。
 */
@Entity
@Table(name = "student",
        uniqueConstraints = @UniqueConstraint(name = "uk_student_no", columnNames = "student_no"),
        indexes = {
                @Index(name = "idx_student_class", columnList = "class_id"),
                @Index(name = "idx_student_school", columnList = "school_id")})
@Data
public class Student {

    /** 学生来源：school=老师录入（校园版）；self=家长自建（未入学自测） */
    public static final String ORIGIN_SCHOOL = "school";
    public static final String ORIGIN_SELF = "self";

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

    /** 学生本人联系电话 */
    @Column(length = 32)
    private String phone;

    /** 身份证号（AES 加密存储，255 容纳密文膨胀；只可写入，禁止序列化输出） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "id_card", length = 255)
    private String idCard;

    /** 身份证后 8 位加密摘要（BCrypt），内部使用，禁止序列化输出 */
    @JsonIgnore
    @Column(name = "id_card_hash", length = 128)
    private String idCardHash;

    /** 父方手机号 */
    @Column(name = "father_phone", length = 20)
    private String fatherPhone;

    /** 母方手机号 */
    @Column(name = "mother_phone", length = 20)
    private String motherPhone;

    /** 来源：school/self，默认 school */
    @Column(length = 16)
    private String origin = ORIGIN_SCHOOL;

    /** 自建学生的创建家长账号 */
    @Column(name = "parent_owner_account_id")
    private Long parentOwnerAccountId;

    /** 是否已入学（false=自测期） */
    private Boolean enrolled = true;

    /** 是否已认领合并（自建→校园），保留审计 */
    private Boolean merged = false;

    /** 校园归属 */
    @Column(name = "school_id")
    private Long schoolId;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
