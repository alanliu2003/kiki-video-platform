package com.kiki.video.api.upload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UploadCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupJob.class);

    private final UploadService uploadService;

    public UploadCleanupJob(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(fixedDelayString = "${app.video.cleanup-interval:15m}")
    public void cleanupExpiredUploads() {
        int cleaned = uploadService.cleanupExpiredSessions();
        if (cleaned > 0) {
            log.info("Expired {} abandoned upload session(s)", cleaned);
        }
    }
}
