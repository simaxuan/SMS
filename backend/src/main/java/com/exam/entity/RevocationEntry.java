package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话失效状态持久化表（取代进程内内存实现，支持重启保留 / 多实例共享）。
 * <p>kind=DENY：登出令牌黑名单（key=jti，expireAt=过期毫秒）；kind=TV：账号 token 版本（key=accountId，val=版本号）。
 */
@Entity
@Table(name = "revocation_entry",
        indexes = {
                @Index(name = "idx_revo_kind_key", columnList = "kind, key_str"),
                @Index(name = "idx_revo_expire", columnList = "expire_at")})
@Data
public class RevocationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DENY=登出黑名单；TV=账号 token 版本。 */
    @Column(name = "kind", nullable = false, length = 8)
    private String kind;

    /** DENY 时为 jti；TV 时为 accountId 字符串（避开 key 保留字，落地为 key_str）。 */
    @Column(name = "key_str", nullable = false, length = 64)
    private String key;

    /** TV 时为版本号；DENY 时恒 1（占位）。 */
    @Column(name = "val", nullable = false)
    private Long val;

    /** DENY 过期毫秒（TV 为 null）。 */
    @Column(name = "expire_at")
    private Long expireAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
