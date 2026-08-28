package com.kiki.video.api.video;

import java.util.UUID;

public final class VideoObjectKeys {

    private VideoObjectKeys() {
    }

    public static String create(long userId, String contentType) {
        String extension = switch (contentType) {
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
        return "videos/" + userId + "/" + UUID.randomUUID() + extension;
    }
}
