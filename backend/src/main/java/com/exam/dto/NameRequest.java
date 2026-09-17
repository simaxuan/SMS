package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NameRequest {

    @NotBlank(message = "名称不能为空")
    private String name;
}
