package com.kiki.video.worker.ffmpeg;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessRunnerConfig {

    @Bean
    public ProcessRunner processRunner() {
        return new ProcessRunner();
    }
}
