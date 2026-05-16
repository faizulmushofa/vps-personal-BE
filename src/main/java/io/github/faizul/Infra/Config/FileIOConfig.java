package io.github.faizul.Infra.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class FileIOConfig {
    final int threadSize = 8;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService fileWriterExecutor() {
        return Executors.newFixedThreadPool(threadSize);
    }
    @Bean
    public Scheduler fileWriteScheduler(ExecutorService fileWriteExecutor) {
        return Schedulers.fromExecutorService(fileWriteExecutor);
    }



}
