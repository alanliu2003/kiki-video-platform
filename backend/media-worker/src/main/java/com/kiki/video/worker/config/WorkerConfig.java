package com.kiki.video.worker.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        WorkerMediaProperties.class,
        WorkerRocketMqProperties.class,
        WorkerMinioProperties.class
})
public class WorkerConfig {

    @Bean
    public MinioClient minioClient(WorkerMinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
