package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.observability.MdcContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSupervisor {

    private final ResumeSpecialistAgent resumeAgent;
    private final JobMatchSpecialistAgent jobMatchAgent;
    private final InterviewSpecialistAgent interviewAgent;

    private static final ExecutorService specialistPool =
        Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setName("specialist-agent-pool");
            t.setDaemon(true);
            return t;
        });

    /**
     * 根据用户消息判断涉及的领域，并行派发给对应专家 Agent，聚合结果。
     * GENERAL 领域不派发专家，直接返回空列表。
     */
    public List<SpecialistResult> dispatch(AgentToolContext context, String userMessage) {
        AgentDomain domain = classifyDomain(userMessage);
        log.info("Supervisor classified domain: {} for message head={}",
            domain, userMessage == null ? "" : userMessage.substring(0, Math.min(50, userMessage.length())));

        if (domain == AgentDomain.GENERAL) {
            return List.of();
        }

        List<CompletableFuture<SpecialistResult>> futures = new ArrayList<>();

        if (domain == AgentDomain.RESUME) {
            futures.add(CompletableFuture.supplyAsync(
                MdcContext.wrap(() -> resumeAgent.process(context, userMessage)), specialistPool));
        } else if (domain == AgentDomain.JOB_MATCH) {
            futures.add(CompletableFuture.supplyAsync(
                MdcContext.wrap(() -> jobMatchAgent.process(context, userMessage)), specialistPool));
        } else if (domain == AgentDomain.INTERVIEW) {
            futures.add(CompletableFuture.supplyAsync(
                MdcContext.wrap(() -> interviewAgent.process(context, userMessage)), specialistPool));
        }

        List<SpecialistResult> results = new ArrayList<>();
        for (CompletableFuture<SpecialistResult> f : futures) {
            try {
                results.add(f.get(8, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("Specialist agent timed out or failed: {}", e.getMessage());
                results.add(SpecialistResult.failed(domain, "specialist timeout"));
            }
        }
        return results;
    }

    /** 关键词规则分类，快速无 LLM 调用。 */
    private AgentDomain classifyDomain(String message) {
        if (message == null || message.isBlank()) return AgentDomain.GENERAL;
        String m = message.toLowerCase();

        if (m.contains("面试") || m.contains("interview") || m.contains("练习") || m.contains("模拟")) {
            return AgentDomain.INTERVIEW;
        }
        if (m.contains("jd") || m.contains("岗位") || m.contains("匹配") || m.contains("职位")
                || m.contains("招聘") || m.contains("技能缺口")) {
            return AgentDomain.JOB_MATCH;
        }
        if (m.contains("简历") || m.contains("resume") || m.contains("经历") || m.contains("项目经验")) {
            return AgentDomain.RESUME;
        }
        if (m.contains("pdf") && (m.contains("生成") || m.contains("下载") || m.contains("导出"))) {
            return AgentDomain.RESUME;
        }
        return AgentDomain.GENERAL;
    }
}
