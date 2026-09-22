package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NameRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    /** 归属学校：全局超管跨校建档时显式指定；普通老师由登录上下文推导，可省略。 */
    private Long schoolId;
}
