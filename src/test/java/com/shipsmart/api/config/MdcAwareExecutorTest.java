package com.shipsmart.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcAwareExecutorTest {

    @Test
    void propagatesMdcAcrossAsyncBoundary() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorConfig.MdcAwareExecutor exec = new ExecutorConfig.MdcAwareExecutor(delegate);
        try {
            MDC.put("requestId", "propagated-id");
            String observed = CompletableFuture.supplyAsync(() -> MDC.get("requestId"), exec).get();
            assertThat(observed).isEqualTo("propagated-id");
        } finally {
            MDC.clear();
            delegate.shutdownNow();
        }
    }
}
