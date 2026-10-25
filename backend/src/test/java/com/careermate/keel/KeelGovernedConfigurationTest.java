package com.careermate.keel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring/security wiring in governed mode, using only the isolated test database. */
@SpringBootTest(properties = {
        "careermate.keel.enabled=true", "careermate.llm.provider=keel",
        "CAREERMATE_KEEL_URL=http://runtime.invalid", "CAREERMATE_SERVICE_TOKEN=configuration-test-service",
        "careermate.llm.endpoint=http://runtime.invalid", "careermate.llm.api-key=configuration-test-service",
        "careermate.agent.framework.enabled=false", "careermate.agent.secondary-llm.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KeelGovernedConfigurationTest {
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test void governedBootSelectsRuntimeAndDisablesLegacyFirstUserBridge() {
        assertThat(context.getBeansOfType(KeelRuntimeClient.class)).hasSize(1);
        assertThat(context.getBeansOfType(KeelBusinessController.class)).hasSize(1);
        assertThat(context.getBeansOfType(KeelInvokeController.class)).isEmpty();
        assertThat(context.getBeansOfType(KeelBridge.class)).isEmpty();
    }

    @Test void internalBusinessEndpointRejectsAnonymousAndWrongServiceCredentials() throws Exception {
        mvc.perform(get("/internal/keel/tools")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/keel/tools").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test void authenticatedSdkCanDiscoverAllBusinessToolsIncludingRagSubpackage() throws Exception {
        mvc.perform(get("/internal/keel/tools").header("Authorization", "Bearer configuration-test-service"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(21))
                .andExpect(jsonPath("$[?(@.function.name == 'rag_retriever')]").isNotEmpty());
    }
}
