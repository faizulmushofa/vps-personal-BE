package io.github.faizul.infra.config;

import io.github.faizul.infra.config.*;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class WorkerConfig {

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
        return Schedulers.newBoundedElastic(20, 10000, "ai-dispatcher");
    }
}
