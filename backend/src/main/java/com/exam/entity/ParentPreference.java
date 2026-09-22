package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 家长端个性化偏好（每家长一条）：
 *  - classScope : 排名只看孩子所在班级（true=班内排名，false=全校排名）
 *  - showTies   : 是否显示并列名次（true=标准并列 1,2,2,4；false=唯一顺延 1,2,3,4）
 */
@Entity
@Table(name = "parent_preference",
        uniqueConstraints = @UniqueConstraint(columnNames = "parent_account_id"))
@Data
public class ParentPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_account_id", nullable = false)
    private Long parentAccountId;

    @Column(name = "class_scope", nullable = false)
    private boolean classScope = true;

    @Column(name = "show_ties", nullable = false)
    private boolean showTies = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
