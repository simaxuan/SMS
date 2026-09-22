package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    /** 登录名：用户名（兼容）或手机号（家长） */
    @NotBlank(message = "用户名/手机号不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过64")
    private String password;

    /** 选择登录角色：TEACHER / PARENT */
    @NotBlank(message = "请选择登录角色")
    private String role;
}
