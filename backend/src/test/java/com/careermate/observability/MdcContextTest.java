package com.careermate.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MdcContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void propagatesMdcToAsyncRunnable() throws Exception {
        MDC.put(MdcKeys.TRACE_ID, "trace-async");
        MDC.put(MdcKeys.REQUEST_ID, "req-async");

        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(MdcContext.wrap(() -> MDC.get(MdcKeys.TRACE_ID)));

        assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("trace-async");
    }
}
