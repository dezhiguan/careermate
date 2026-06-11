package com.careermate.resume.version;

import com.careermate.common.api.ApiResponse;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resume-version")
public class ResumeVersionController {

    private final ResumeVersionService resumeVersionService;

    public ResumeVersionController(ResumeVersionService resumeVersionService) {
        this.resumeVersionService = resumeVersionService;
    }

    @GetMapping("/list")
    public ApiResponse<List<ResumeVersionListItemVO>> list(
            @RequestParam(required = false) String sessionId
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(resumeVersionService.listBySession(userId, sessionId));
    }

    @GetMapping("/{versionId}/export/pdf")
    public void exportPdf(@PathVariable String versionId, HttpServletResponse response) {
        resumeVersionService.exportPdf(versionId, response);
    }

    @GetMapping("/{versionId}")
    public ApiResponse<ResumeVersionVO> detail(@PathVariable String versionId) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(resumeVersionService.getVersion(userId, versionId));
    }
}
