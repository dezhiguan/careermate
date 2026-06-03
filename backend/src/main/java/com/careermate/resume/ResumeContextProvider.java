package com.careermate.resume;

import com.careermate.model.entity.ResumeEntity;
import com.careermate.security.CurrentUserContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ResumeContextProvider {

    private static final String EMPTY_CONTEXT_TEXT = "当前用户暂无默认简历。";

    private final ResumeService resumeService;

    public ResumeContextProvider(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    public ResumeContext getCurrentUserResumeContext() {
        return getResumeContext(CurrentUserContext.getUserId());
    }

    public ResumeContext getResumeContext(Long userId) {
        if (userId == null) {
            return emptyContext();
        }
        Optional<ResumeEntity> resumeOpt = resumeService.getDefaultActiveResume(userId);
        if (resumeOpt.isEmpty()) {
            return emptyContext();
        }
        ResumeEntity resume = resumeOpt.get();
        String contextText = buildContextText(resume.getTitle(), resume.getContent());
        return ResumeContext.builder()
                .available(true)
                .resumeId(resume.getId())
                .title(resume.getTitle())
                .content(resume.getContent())
                .contextText(contextText)
                .build();
    }

    private ResumeContext emptyContext() {
        return ResumeContext.builder()
                .available(false)
                .contextText(EMPTY_CONTEXT_TEXT)
                .build();
    }

    private String buildContextText(String title, String content) {
        String safeTitle = title == null ? "" : title.trim();
        String safeContent = content == null ? "" : content.trim();
        return """
                用户默认简历：
                标题：%s
                正文：
                %s
                """.formatted(safeTitle, safeContent).trim();
    }
}
