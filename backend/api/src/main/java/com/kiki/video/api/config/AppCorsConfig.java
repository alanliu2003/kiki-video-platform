package com.kiki.video.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class AppCorsConfig {

    @Bean
    public WebMvcConfigurer appCorsConfigurer(CorsProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (CollectionUtils.isEmpty(properties.allowedOrigins())) {
                    return;
                }
                String[] origins = properties.allowedOrigins().toArray(String[]::new);
                for (String origin : origins) {
                    if ("*".equals(origin)) {
                        throw new IllegalStateException(
                                "app.cors.allowed-origins must not be '*' when the API may send credentials"
                        );
                    }
                }
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length", "X-Request-ID");
                registry.addMapping("/ws/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET")
                        .allowedHeaders("*")
                        .allowCredentials(false);
            }
        };
    }
}
