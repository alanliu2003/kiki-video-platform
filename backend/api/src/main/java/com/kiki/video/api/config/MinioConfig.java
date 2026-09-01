package com.kiki.video.api.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties({MinioProperties.class, MediaDeliveryProperties.class, CorsProperties.class})
public class MinioConfig {

    @Bean
    @Primary
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    @Qualifier("minioSigningClient")
    public MinioClient minioSigningClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.signingEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
