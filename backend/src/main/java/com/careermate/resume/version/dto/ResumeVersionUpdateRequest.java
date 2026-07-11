package com.careermate.resume.version.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeVersionUpdateRequest {

    // versionName 与 contentMarkdown 均为可选：支持“仅改名”或“仅改内容”，
    // 服务层要求至少提供其一。长度上限与 DB 列（version_name 已扩至 255）对齐。
    @Size(max = 128, message = "版本名最长128字符")
    private String versionName;

    @Size(max = 50000, message = "简历内容最长50000字符")
    private String contentMarkdown;
}
