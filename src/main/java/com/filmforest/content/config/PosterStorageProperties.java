package com.filmforest.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** admin-server 写入、client-server 读取的共享海报文件目录。 */
@ConfigurationProperties(prefix = "app.poster")
public record PosterStorageProperties(String storageDir) {

    public Path root() {
        if (storageDir == null || storageDir.isBlank()) {
            throw new IllegalStateException("海报本地存储目录未配置");
        }
        return Path.of(storageDir).toAbsolutePath().normalize();
    }
}
