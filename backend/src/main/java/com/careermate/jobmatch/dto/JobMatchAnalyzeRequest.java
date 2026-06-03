package com.careermate.jobmatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobMatchAnalyzeRequest {

    @NotBlank(message = "jobTitle不能为空")
    @Size(max = 128, message = "jobTitle最长128字符")
    private String jobTitle;

    @Size(max = 128, message = "companyName最长128字符")
    private String companyName;

    @NotBlank(message = "jdContent不能为空")
    @Size(max = 50000, message = "jdContent最长50000字符")
    private String jdContent;
}
