package com.shipsmart.api.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.micrometer.core.instrument.Tag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Bounded, observable executors for the two real multi-threaded paths:
 *   - Quote-provider fanout (outbound HTTP; short-lived; timeout-protected).
 *   - Audit writes (fire-and-forget; never drop under backpressure).
 *
 * Both wrap {@link MdcAwareExecutor} so logs keep the right requestId/traceId.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService quoteProviderExecutor(
            @Value("${shipsmart.executor.quote-provider.core-pool-size:4}") int core,
            @Value("${shipsmart.executor.quote-provider.max-pool-size:8}") int max,
            @Value("${shipsmart.executor.quote-provider.queue-capacity:100}") int queue,
            MeterRegistry meters) {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                core, max, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                namedFactory("quote-provider"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        ExecutorService wrapped = new MdcAwareExecutor(tpe);
        return ExecutorServiceMetrics.monitor(meters, wrapped, "quote-provider", List.<Tag>of());
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService auditExecutor(
            @Value("${shipsmart.executor.audit.core-pool-size:2}") int core,
            @Value("${shipsmart.executor.audit.max-pool-size:2}") int max,
            @Value("${shipsmart.executor.audit.queue-capacity:500}") int queue,
            MeterRegistry meters) {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                core, max, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                namedFactory("audit"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        ExecutorService wrapped = new MdcAwareExecutor(tpe);
        return ExecutorServiceMetrics.monitor(meters, wrapped, "audit", List.<Tag>of());
    }

    private static ThreadFactory namedFactory(String baseName) {
        return r -> {
            Thread t = new Thread(r, baseName + "-" + Thread.currentThread().getId());
            t.setDaemon(true);
            return t;
        };
    }

    /** Snapshots MDC at submit time and restores on the worker thread. */
    static final class MdcAwareExecutor extends AbstractExecutorService {
        private final ExecutorService delegate;
        MdcAwareExecutor(ExecutorService delegate) { this.delegate = delegate; }

        @Override public void execute(Runnable command) {
            Map<String, String> snapshot = MDC.getCopyOfContextMap();
            delegate.execute(() -> {
                Map<String, String> prev = MDC.getCopyOfContextMap();
                if (snapshot != null) MDC.setContextMap(snapshot); else MDC.clear();
                try { command.run(); }
                finally {
                    if (prev != null) MDC.setContextMap(prev); else MDC.clear();
                }
            });
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public java.util.List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long t, TimeUnit u) throws InterruptedException {
            return delegate.awaitTermination(t, u);
        }
    }
}
