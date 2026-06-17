package com.careermate.agent.multiagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AgentSupervisorRoute(
        List<AgentDomain> selectedDomains,
        AgentDomain primaryDomain,
        double confidence,
        String reasonCode,
        boolean requiresCritic
) {

    private static final int MAX_BUSINESS_DOMAINS = 3;

    public AgentSupervisorRoute {
        selectedDomains = normalizeDomains(selectedDomains);
        if (primaryDomain == null) {
            primaryDomain = selectedDomains.isEmpty() ? AgentDomain.GENERAL : selectedDomains.get(0);
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            reasonCode = "UNSPECIFIED";
        }
        confidence = Math.max(0D, Math.min(1D, confidence));
    }

    public static AgentSupervisorRoute empty() {
        return new AgentSupervisorRoute(List.of(), AgentDomain.GENERAL, 0D, "GENERAL", false);
    }

    public boolean hasBusinessDomains() {
        return selectedDomains.stream().anyMatch(AgentSupervisorRoute::isBusinessDomain);
    }

    public List<AgentDomain> businessDomains() {
        return selectedDomains.stream()
                .filter(AgentSupervisorRoute::isBusinessDomain)
                .limit(MAX_BUSINESS_DOMAINS)
                .toList();
    }

    private static List<AgentDomain> normalizeDomains(List<AgentDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        List<AgentDomain> distinct = new ArrayList<>();
        for (AgentDomain domain : domains) {
            if (domain == null || domain == AgentDomain.GENERAL || domain == AgentDomain.CRITIC) {
                continue;
            }
            if (!distinct.contains(domain)) {
                distinct.add(domain);
            }
            if (distinct.size() >= MAX_BUSINESS_DOMAINS) {
                break;
            }
        }
        return Collections.unmodifiableList(distinct);
    }

    private static boolean isBusinessDomain(AgentDomain domain) {
        return domain != null && domain != AgentDomain.GENERAL && domain != AgentDomain.CRITIC;
    }
}
