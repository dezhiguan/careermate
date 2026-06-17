package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CriticAgent implements SpecialistAgent {

    private static final SpecialistAgentSpec SPEC = new SpecialistAgentSpec(
            AgentDomain.CRITIC,
            "CriticAgent",
            "审查简历造假风险并拦截编造经历/项目/学历/证书的请求，不调用写产物工具。",
            List.of(),
            SpecialistAgentSpec.INPUT_SCHEMA_V1,
            SpecialistAgentSpec.OUTPUT_SCHEMA_V1
    );

    @Override
    public SpecialistAgentSpec spec() {
        return SPEC;
    }

    @Override
    public boolean supports(AgentSupervisorRoute route) {
        return route != null && route.requiresCritic();
    }

    @Override
    public SpecialistResult process(AgentToolContext context, String userMessage, AgentSupervisorRoute route) {
        if (!FabricationRiskDetector.detect(userMessage)) {
            return SpecialistResult.builder()
                    .domain(AgentDomain.CRITIC)
                    .agentName(agentName())
                    .intent("RISK_REVIEW")
                    .status(SpecialistResultStatus.NO_ACTION)
                    .riskLevel(SpecialistRiskLevel.LOW)
                    .summary("未发现需要拦截的简历造假风险。")
                    .build();
        }
        log.info("Critic blocked fabrication request, sessionId={}", context == null ? null : context.getSessionId());
        return SpecialistResult.builder()
                .domain(AgentDomain.CRITIC)
                .agentName(agentName())
                .intent("FABRICATION_BLOCK")
                .status(SpecialistResultStatus.BLOCKED)
                .riskLevel(SpecialistRiskLevel.HIGH)
                .summary("不能编造或夸大未发生的项目、公司、学历或证书。请仅优化真实经历，"
                        + "或将学习/练习项目明确标注为“个人练习项目”。")
                .warnings(List.of(
                        "不得执行会写入虚假经历的工具",
                        "不得帮用户伪造大厂项目或学历信息"
                ))
                .structuredData(Map.of(
                        "reasonCode", "FABRICATION_REQUEST",
                        "blocked", true
                ))
                .build();
    }
}
