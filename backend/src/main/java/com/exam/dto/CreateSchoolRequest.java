package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建/更新学校请求（多租户根）。 */
@Data
public class CreateSchoolRequest {

    @NotBlank(message = "学校名称不能为空")
    private String name;

    @NotBlank(message = "学校编码不能为空")
    private String code;
}
