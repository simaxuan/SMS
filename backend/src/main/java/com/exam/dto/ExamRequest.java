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

    private LocalDate examDate;
}
