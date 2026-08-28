package com.kiki.video.worker.ffmpeg;

import com.kiki.video.common.media.Rendition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MasterPlaylistWriter {

    private MasterPlaylistWriter() {
    }

    public static void write(Path destination, List<Rendition> renditions, int sourceWidth, int sourceHeight) {
        StringBuilder builder = new StringBuilder();
        builder.append("#EXTM3U\n");
        builder.append("#EXT-X-VERSION:3\n");
        for (Rendition rendition : renditions) {
            builder.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(rendition.bandwidthBps())
                    .append(",RESOLUTION=")
                    .append(rendition.resolution(sourceWidth, sourceHeight))
                    .append(",NAME=\"")
                    .append(rendition.name())
                    .append("\"\n");
            builder.append(rendition.name()).append("/index.m3u8\n");
        }
        try {
            Files.writeString(destination, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write master playlist", ex);
        }
    }
}
