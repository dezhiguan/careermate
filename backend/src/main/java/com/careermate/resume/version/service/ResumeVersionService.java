package com.careermate.resume.version.service;

import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.dto.ResumeVersionVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public interface ResumeVersionService {

    ResumeVersionVO getVersion(Long userId, String versionId);

    ResumeVersionVO updateVersion(Long userId, String versionId, String versionName, String contentMarkdown);

    List<ResumeVersionListItemVO> listBySession(Long userId, String sessionId);

    ResumeVersionVO createVersion(
            Long userId,
            String sessionId,
            Long sourceResumeId,
            String targetJdId,
            String targetJdLabel,
            String versionName,
            String contentMarkdown,
            List<Map<String, Object>> optimizationNotes
    );

    /**
     * 导出指定版本简历为 PDF，写入 response 输出流。
     *
     * @param versionId 版本 ID
     * @param response  HTTP 响应对象
     */
    void exportPdf(Long userId, String versionId, HttpServletResponse response);

    void exportDocx(Long userId, String versionId, HttpServletResponse response);

    void deleteVersion(Long userId, String versionId);
}
