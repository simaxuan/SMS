package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64之间")
    private String password;

    /** 注册角色：TEACHER / PARENT */
    @NotBlank(message = "请选择角色")
    private String role;

    @Size(max = 32, message = "昵称长度不能超过32")
    private String nickname;

    /** 手机号（家长登录名/校验凭据；唯一） */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;
}
