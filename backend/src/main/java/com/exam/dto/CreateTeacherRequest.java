package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 教师账号创建请求（管理员/校级/全校超管调用）。
 * 强制归属学校；CLASS 范围必须绑定本校班级。
 */
@Data
public class CreateTeacherRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64之间")
    private String password;

    @Size(max = 32, message = "昵称长度不能超过32")
    private String nickname;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    /** 归属学校（多租户前提，必填） */
    @NotNull(message = "教师必须归属学校")
    private Long schoolId;

    /** 数据范围：CLASS / SCHOOL / ALL，默认 CLASS */
    private String scopeType;

    /** CLASS 范围才必填：绑定班级 ID 集合（须属于 schoolId 所在校） */
    private List<Long> classIds;
}
