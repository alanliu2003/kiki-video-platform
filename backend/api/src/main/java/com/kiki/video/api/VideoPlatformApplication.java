package com.kiki.video.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.kiki.video")
public class VideoPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoPlatformApplication.class, args);
    }
}
