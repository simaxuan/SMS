package com.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GradeRequest {

    @NotNull(message = "学生不能为空")
    private Long studentId;

    @NotNull(message = "考试不能为空")
    private Long examId;

    @NotNull(message = "课程不能为空")
    private Long courseId;

    @NotNull(message = "分数不能为空")
    @Min(value = 0, message = "分数不能小于0")
    @Max(value = 100, message = "分数不能大于100")
    private Integer score;
}
