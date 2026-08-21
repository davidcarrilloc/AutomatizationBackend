package com.mx.liverpool.automatizacionbackend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "fulfillmentExecutor")
    public TaskExecutor fulfillmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("fulfillment-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "statusOmsExecutor")
    public TaskExecutor statusOmsExecutor() {
        // Un hilo virtual por job: varios jobs de statusOms corren a la vez, sin cola ni tope.
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("status-oms-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
