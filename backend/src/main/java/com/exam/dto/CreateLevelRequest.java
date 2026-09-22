package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 创建/更新级部（年级）请求，归属某一学校。 */
@Data
public class CreateLevelRequest {

    @NotNull(message = "schoolId 不能为空")
    private Long schoolId;

    @NotBlank(message = "级部名称不能为空")
    private String name;
}
