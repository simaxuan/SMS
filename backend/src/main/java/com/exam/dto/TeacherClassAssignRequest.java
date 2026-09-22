package com.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 老师-班级归属绑定请求：将一个老师账号绑定到多个班级。 */
@Data
public class TeacherClassAssignRequest {

    @NotNull(message = "teacherAccountId 不能为空")
    private Long teacherAccountId;

    @NotNull(message = "classIds 不能为空")
    private List<Long> classIds;
}
