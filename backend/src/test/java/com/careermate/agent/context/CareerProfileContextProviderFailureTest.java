package com.careermate.agent.context;

import com.careermate.agent.memory.AgentMemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerProfileContextProviderFailureTest {

    @Mock
    private AgentMemoryService agentMemoryService;

    @InjectMocks
    private CareerProfileContextProvider careerProfileContextProvider;

    @Test
    void returnsFailedResultWhenMemoryLoadThrows() {
        when(agentMemoryService.loadMemoryContext(anyLong(), any()))
                .thenThrow(new RuntimeException("memory db unavailable"));

        CareerProfileContextResult result = careerProfileContextProvider.load(900001L, "session-1");

        assertFalse(result.isAvailable());
        assertTrue(result.isFailed());
        assertEquals("MEMORY_LOAD_FAILED", result.getErrorCode());
    }
}
