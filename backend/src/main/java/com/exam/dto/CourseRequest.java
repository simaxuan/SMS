package com.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseRequest {

    @NotBlank(message = "课程名称不能为空")
    private String name;

    /** 课程默认满分（如语文 100 或 120） */
    @Min(value = 1, message = "满分必须大于0")
    private Integer fullScore = 100;

    /** 归属学校：全局超管跨校建档时显式指定；普通老师由登录上下文推导，可省略。 */
    private Long schoolId;
}
