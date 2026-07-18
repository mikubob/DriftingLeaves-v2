package com.xuan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling//启用定时任务
@EnableAsync//启用异步任务
@EnableCaching//启用缓存注解功能
@EnableTransactionManagement//启用事务
@Slf4j
public class DriftingLeavesApplication {

    public static void main(String[] args) {
        log.info("启动中...");
        SpringApplication.run(DriftingLeavesApplication.class, args);
        log.info("启动成功...");
    }
}
