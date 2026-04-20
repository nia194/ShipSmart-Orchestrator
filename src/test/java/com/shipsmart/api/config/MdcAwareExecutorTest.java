package com.shipsmart.api.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class MdcAwareExecutorTest {

    @Test
    void propagatesMdcAcrossAsyncBoundary() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorConfig.MdcAwareExecutor exec = new ExecutorConfig.MdcAwareExecutor(delegate);
        try {
            MDC.put("requestId", "propagated-id");
            String observed = CompletableFuture
                    .supplyAsync(() -> MDC.get("requestId"), exec)
                    .get();
            assertThat(observed).isEqualTo("propagated-id");
        } finally {
            MDC.clear();
            delegate.shutdownNow();
        }
    }
}
