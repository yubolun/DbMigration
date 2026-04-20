package com.dbmigration.config;

import com.dbmigration.common.AesUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步 & 虚拟线程配置
 */
@Configuration
public class AsyncConfig {

    @Value("${app.aes-key}")
    private String aesKey;

    /**
     * 初始化 AES 加密密钥
     */
    @PostConstruct
    public void initAes() {
        AesUtils.init(aesKey);
    }

    /**
     * 虚拟线程执行器 - 用于同步任务执行
     */
    @Bean("syncExecutor")
    public ExecutorService syncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 定时任务调度器
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sync-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
