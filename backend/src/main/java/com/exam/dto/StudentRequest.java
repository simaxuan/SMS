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
}
