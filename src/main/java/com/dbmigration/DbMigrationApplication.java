package com.dbmigration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DB Migration 异构数据库同步平台 启动类
 */
@SpringBootApplication
@MapperScan("com.dbmigration.**.mapper")
@EnableAsync
@EnableScheduling
public class DbMigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationApplication.class, args);
    }
}
