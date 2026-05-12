package com.filmforest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({"com.filmforest.crawler.mapper", "com.filmforest.content.mapper", "com.filmforest.resource.mapper", "com.filmforest.settings.mapper", "com.filmforest.content.mapper"})
@EnableAsync
@EnableScheduling
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
