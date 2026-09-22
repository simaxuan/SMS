package com.exam.dto;

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
    private Integer score;

    /** 本次考试该课程满分（不设上限，默认取课程默认满分） */
    @Min(value = 1, message = "满分必须大于0")
    private Integer fullScore = 100;
}
