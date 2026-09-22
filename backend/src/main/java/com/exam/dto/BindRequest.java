package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BindRequest {

    @NotBlank(message = "学号不能为空")
    @Size(max = 32, message = "学号长度不能超过32")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64")
    private String name;

    /** 家长手机号，需与学生 father_phone/mother_phone 之一匹配 */
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    /** 学生身份证后 8 位（不落库，仅比对） */
    @Size(max = 8, message = "身份证后8位长度不能超过8")
    private String idCardLast8;

    /** 与孩子的关系：father/mother/other */
    @Size(max = 16, message = "关系长度不能超过16")
    private String relation;

    /** 绑定默认密码（=身份证后8位） */
    @Size(max = 64, message = "密码长度不能超过64")
    private String password;
}
