package com.exam.dto;

import lombok.Data;

/**
 * 创建/更新班级请求。
 * levelId/schoolId 可选：为空时由登录上下文推导（多租户隔离）。
 * 兼容旧调用（仅传 name）时两列留空，不破坏既有测试。
 */
@Data
public class CreateClassRequest {

    private String name;

    private Long levelId;

    private Long schoolId;
}
