package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class StudentRequest {

    @NotBlank(message = "学号不能为空")
    @Size(max = 32, message = "学号长度不能超过32")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64")
    private String name;

    @Size(max = 8, message = "性别长度不能超过8")
    private String gender;

    private Long classId;

    private LocalDate birthDate;

    @Size(max = 32, message = "联系电话长度不能超过32")
    private String phone;

    /** 身份证号（AES 加密存储；列表不返回） */
    @Size(max = 18, message = "身份证号长度为18位")
    private String idCard;

    /** 父方手机号 */
    @Size(max = 20, message = "父方手机号长度不能超过20")
    private String fatherPhone;

    /** 母方手机号 */
    @Size(max = 20, message = "母方手机号长度不能超过20")
    private String motherPhone;

    /** 归属学校（全局超管建学生时显式指定，可跨校；普通老师忽略，走 login 校） */
    private Long schoolId;
}
