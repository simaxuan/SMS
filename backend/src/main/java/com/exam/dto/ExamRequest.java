package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ExamRequest {

    @NotBlank(message = "考试名称不能为空")
    @Size(max = 128, message = "考试名称长度不能超过128")
    private String name;

    private Long examTypeId;

    private LocalDate examDate;

    @Size(max = 32, message = "学期长度不能超过32")
    private String semester;

    /** 归属学校（全局超管建考试时显式指定，可跨校；普通老师忽略，走 resolveWriteSchoolId 锁定登录校） */
    private Long schoolId;
}
