package com.kiki.video.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.kiki.video",
        exclude = UserDetailsServiceAutoConfiguration.class
)
@EnableScheduling
@MapperScan({
        "com.kiki.video.api.user.mapper",
        "com.kiki.video.api.video.mapper",
        "com.kiki.video.api.upload.mapper"
})
public class VideoPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoPlatformApplication.class, args);
    }
}
