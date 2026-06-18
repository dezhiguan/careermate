package com.careermate.resume.version.workflow;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.version.dto.ResumeVersionVO;

import java.util.List;
import java.util.Map;

/**
 * 单次 Workflow 执行的运行时上下文，在各 step 间传递状态。
 */
public class GenerateResumeWorkflowRun {

    private final Long userId;
    private final String sessionId;
    private final String jdId;
    private final SseEmitterService sseEmitterService;

    private AgentSessionEntity session;
    private Map<String, Object> jdSnapshot = Map.of();
    private ResumeContext resumeContext;
    private String jdContent;
    private String company;
    private String title;
    private String versionName;
    private String targetLabel;
    private String gapSummary;
    private String rawLlmOutput;
    private String markdown;
    private String changeSummary;
    private List<Map<String, Object>> optimizationNotes = List.of();
    private ResumeVersionVO savedVersion;
    private Map<String, Object> card;
    private String resumePromptId;
    private String resumePromptVersion;

    public GenerateResumeWorkflowRun(Long userId, String sessionId, String jdId, SseEmitterService sseEmitterService) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.jdId = jdId;
        this.sseEmitterService = sseEmitterService;
    }

    public Long userId() {
        return userId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String jdId() {
        return jdId;
    }

    public SseEmitterService sseEmitterService() {
        return sseEmitterService;
    }

    public AgentSessionEntity session() {
        return session;
    }

    public void setSession(AgentSessionEntity session) {
        this.session = session;
    }

    public Map<String, Object> jdSnapshot() {
        return jdSnapshot;
    }

    public void setJdSnapshot(Map<String, Object> jdSnapshot) {
        this.jdSnapshot = jdSnapshot != null ? jdSnapshot : Map.of();
    }

    public ResumeContext resumeContext() {
        return resumeContext;
    }

    public void setResumeContext(ResumeContext resumeContext) {
        this.resumeContext = resumeContext;
    }

    public String jdContent() {
        return jdContent;
    }

    public void setJdContent(String jdContent) {
        this.jdContent = jdContent;
    }

    public String company() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String versionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String targetLabel() {
        return targetLabel;
    }

    public void setTargetLabel(String targetLabel) {
        this.targetLabel = targetLabel;
    }

    public String gapSummary() {
        return gapSummary;
    }

    public void setGapSummary(String gapSummary) {
        this.gapSummary = gapSummary;
    }

    public String rawLlmOutput() {
        return rawLlmOutput;
    }

    public void setRawLlmOutput(String rawLlmOutput) {
        this.rawLlmOutput = rawLlmOutput;
    }

    public String markdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String changeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public List<Map<String, Object>> optimizationNotes() {
        return optimizationNotes;
    }

    public void setOptimizationNotes(List<Map<String, Object>> optimizationNotes) {
        this.optimizationNotes = optimizationNotes != null ? optimizationNotes : List.of();
    }

    public ResumeVersionVO savedVersion() {
        return savedVersion;
    }

    public void setSavedVersion(ResumeVersionVO savedVersion) {
        this.savedVersion = savedVersion;
    }

    public Map<String, Object> card() {
        return card;
    }

    public void setCard(Map<String, Object> card) {
        this.card = card;
    }

    public String resumePromptId() {
        return resumePromptId;
    }

    public void setResumePromptId(String resumePromptId) {
        this.resumePromptId = resumePromptId;
    }

    public String resumePromptVersion() {
        return resumePromptVersion;
    }

    public void setResumePromptVersion(String resumePromptVersion) {
        this.resumePromptVersion = resumePromptVersion;
    }
}
