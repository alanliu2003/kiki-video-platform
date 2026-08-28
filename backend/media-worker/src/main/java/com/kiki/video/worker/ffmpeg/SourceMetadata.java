package com.kiki.video.worker.ffmpeg;

public record SourceMetadata(
        double durationSeconds,
        int width,
        int height,
        String videoCodec,
        String audioCodec
) {

    public boolean hasAudio() {
        return audioCodec != null && !audioCodec.isBlank();
    }
}
