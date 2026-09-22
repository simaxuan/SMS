package com.exam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 账号。依据《统一数据库字段规范》2.5。
 * 新增：phone(唯一)、schoolId、scopeType、微信预留(openid/unionid/auth_type)、lastPasswordChange。
 */
@Entity
@Table(name = "account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_account_phone", columnNames = "phone")},
        indexes = {@Index(name = "idx_account_school", columnList = "school_id")})
@Data
public class Account {

    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_PARENT = "PARENT";

    /** 数据范围类型：CLASS=所属班级（默认）；SCHOOL=仅看本校全部班级；ALL=全校（叠加 schoolId 兜底，见 DataScopeService） */
    public static final String SCOPE_CLASS = "CLASS";
    public static final String SCOPE_SCHOOL = "SCHOOL";
    public static final String SCOPE_ALL = "ALL";

    /** 登录方式：password=手机号+密码（默认）。wechat/phone 登录方式预留给后续版本，其常量待引入时再定义。 */
    public static final String AUTH_PASSWORD = "password";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    @JsonIgnore // 纵深防护：密码哈希绝不出现在任何 JSON 序列化输出（实体直出兜底）
    private String passwordHash;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(length = 32)
    private String nickname;

    /** 手机号（唯一，家长登录名/校验凭据） */
    @Column(length = 20)
    private String phone;

    /** 所属校园 */
    @Column(name = "school_id")
    private Long schoolId;

    /** 数据范围类型：CLASS/SCHOOL/ALL，默认 CLASS */
    @Column(name = "scope_type", length = 16)
    private String scopeType = SCOPE_CLASS;

    /** 微信预留 */
    @Column(name = "wechat_openid", length = 64)
    private String wechatOpenid;

    @Column(name = "wechat_unionid", length = 64)
    private String wechatUnionid;

    /** 登录方式：password/wechat/phone，默认 password */
    @Column(name = "auth_type", length = 16)
    private String authType = AUTH_PASSWORD;

    /** 最近一次改密时间（审计） */
    @Column(name = "last_password_change")
    private LocalDateTime lastPasswordChange;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
