package io.github.faizul.infra.config;

import io.github.faizul.infra.config.*;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@Slf4j
public class WorkerConfig {

    @PostConstruct
    public void init() {
        Hooks.onErrorDropped(throwable -> {
            if (isCancellation(throwable)) {
                log.debug("Reactor dropped error due to cancellation/interruption: {}", throwable.getMessage());
            } else {
                log.error("Operator called default onErrorDropped", throwable);
            }
        });
    }

    private static boolean isCancellation(Throwable t) {
        if (t == null) return false;
        if (t instanceof InterruptedException ||
            t instanceof java.io.InterruptedIOException ||
            t instanceof java.util.concurrent.CancellationException) {
            return true;
        }
        String msg = t.getMessage();
        if (msg != null && (
            msg.contains("interrupted") || 
            msg.contains("InterruptedException") || 
            msg.contains("InterruptedIOException") || 
            msg.contains("cancel")
        )) {
            return true;
        }
        if (t.getCause() != null && isCancellation(t.getCause())) {
            return true;
        }
        for (Throwable suppressed : t.getSuppressed()) {
            if (isCancellation(suppressed)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public Scheduler fileWriteScheduler() {
        return Schedulers.newBoundedElastic(10, 100000, "file-writer");
    }

    @Bean
    public Scheduler fileCleanupScheduler() {
        return Schedulers.newBoundedElastic(10, 100000, "file-cleaner");
    }

    @Bean
    public Scheduler grpcDispatchScheduler() {
        return Schedulers.newBoundedElastic(20, 10000, "grpc-dispatcher");
    }

    @Bean
    public Scheduler aiScheduler() {
        return Schedulers.newBoundedElastic(10, 1000, "ai-dispatcher");
    }

    @Bean
    public Scheduler pdfScheduler() {
        return Schedulers.newBoundedElastic(15, 1000, "files-dispatcher");
    }

    @Bean
    public Scheduler googleSyncScheduler() {
        return Schedulers.newBoundedElastic(10, 1000, "google-sync");
    }

}
