package com.kiki.video.api.video.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class MinioBucketInitializer implements ApplicationRunner {

    private final VideoStorage videoStorage;

    public MinioBucketInitializer(VideoStorage videoStorage) {
        this.videoStorage = videoStorage;
    }

    @Override
    public void run(ApplicationArguments args) {
        videoStorage.ensureReady();
    }
}
