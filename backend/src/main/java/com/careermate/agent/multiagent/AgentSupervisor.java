package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.observability.MdcContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSupervisor {

    private static final int MAX_TOTAL_RESULTS = 4;
    private static final long SPECIALIST_TIMEOUT_SECONDS = 8L;

    private final AgentSupervisorRouter router;
    private final CriticAgent criticAgent;
    private final ResumeSpecialistAgent resumeAgent;
    private final JobMatchSpecialistAgent jobMatchAgent;
    private final InterviewSpecialistAgent interviewAgent;
    private final MarketSpecialistAgent marketAgent;

    private static final ExecutorService specialistPool =
            Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r);
                t.setName("specialist-agent-pool");
                t.setDaemon(true);
                return t;
            });

    public List<SpecialistResult> dispatch(AgentToolContext context, String userMessage) {
        AgentSupervisorRoute resolvedRoute = router.route(userMessage);
        if (resolvedRoute == null) {
            resolvedRoute = AgentSupervisorRoute.empty();
        }
        final AgentSupervisorRoute route = resolvedRoute;
        log.info("Supervisor route: domains={} primary={} critic={} reason={}",
                route.selectedDomains(),
                route.primaryDomain(),
                route.requiresCritic(),
                route.reasonCode());

        if (!route.hasBusinessDomains() && !route.requiresCritic()) {
            return List.of();
        }

        List<SpecialistResult> results = new ArrayList<>();

        if (route.requiresCritic() || FabricationRiskDetector.detect(userMessage)) {
            SpecialistResult criticResult = criticAgent.process(context, userMessage, route);
            if (criticResult.getStatus() == SpecialistResultStatus.BLOCKED) {
                results.add(criticResult);
                return results;
            }
        }

        Map<AgentDomain, SpecialistAgent> agents = businessAgents();
        List<CompletableFuture<SpecialistResult>> futures = new ArrayList<>();
        for (AgentDomain domain : route.businessDomains()) {
            SpecialistAgent agent = agents.get(domain);
            if (agent == null || !agent.supports(route)) {
                continue;
            }
            futures.add(CompletableFuture.supplyAsync(
                    MdcContext.wrap(() -> agent.process(context, userMessage, route)),
                    specialistPool
            ));
        }

        for (CompletableFuture<SpecialistResult> future : futures) {
            try {
                SpecialistResult result = future.get(SPECIALIST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("Specialist agent timed out or failed: {}", e.getMessage());
                results.add(SpecialistResult.failed(route.primaryDomain(), "specialist timeout"));
            }
            if (results.size() >= MAX_TOTAL_RESULTS) {
                break;
            }
        }
        return results;
    }

    private Map<AgentDomain, SpecialistAgent> businessAgents() {
        Map<AgentDomain, SpecialistAgent> agents = new LinkedHashMap<>();
        agents.put(AgentDomain.RESUME, resumeAgent);
        agents.put(AgentDomain.JOB_MATCH, jobMatchAgent);
        agents.put(AgentDomain.INTERVIEW, interviewAgent);
        agents.put(AgentDomain.MARKET, marketAgent);
        return agents;
    }
}
