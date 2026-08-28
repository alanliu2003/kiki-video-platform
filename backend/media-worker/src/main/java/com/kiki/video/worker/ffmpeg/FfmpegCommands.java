package com.kiki.video.worker.ffmpeg;

import com.kiki.video.common.media.Rendition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FfmpegCommands {

    private FfmpegCommands() {
    }

    public static List<String> ffprobe(String ffprobePath, Path source) {
        return List.of(
                ffprobePath,
                "-v", "error",
                "-show_entries", "stream=codec_type,codec_name,width,height,duration",
                "-show_entries", "format=duration",
                "-of", "json",
                source.toAbsolutePath().toString()
        );
    }

    public static List<String> rendition(
            String ffmpegPath,
            Path source,
            Path outputDir,
            Rendition rendition,
            SourceMetadata metadata,
            int segmentDuration
    ) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(source.toAbsolutePath().toString());
        command.add("-map");
        command.add("0:v:0");
        if (metadata.hasAudio()) {
            command.add("-map");
            command.add("0:a:0?");
        }
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-profile:v");
        command.add("main");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-b:v");
        command.add(rendition.videoBitrateKbps() + "k");
        command.add("-maxrate");
        command.add(rendition.videoBitrateKbps() + "k");
        command.add("-bufsize");
        command.add((rendition.videoBitrateKbps() * 2) + "k");
        command.add("-vf");
        command.add("scale=-2:" + rendition.height());
        if (metadata.hasAudio()) {
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add(rendition.audioBitrateKbps() + "k");
            command.add("-ac");
            command.add("2");
        } else {
            command.add("-an");
        }
        command.add("-hls_time");
        command.add(Integer.toString(segmentDuration));
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_filename");
        command.add(outputDir.resolve("segment%03d.ts").toAbsolutePath().toString());
        command.add(outputDir.resolve("index.m3u8").toAbsolutePath().toString());
        return List.copyOf(command);
    }

    public static List<String> thumbnail(String ffmpegPath, Path source, Path output, double seekSeconds) {
        return List.of(
                ffmpegPath,
                "-y",
                "-ss",
                String.format(java.util.Locale.ROOT, "%.3f", seekSeconds),
                "-i",
                source.toAbsolutePath().toString(),
                "-frames:v",
                "1",
                "-q:v",
                "2",
                output.toAbsolutePath().toString()
        );
    }

    public static double thumbnailOffset(double durationSeconds) {
        if (durationSeconds <= 2) {
            return Math.max(0, durationSeconds * 0.1);
        }
        return Math.min(durationSeconds * 0.1, durationSeconds - 0.1);
    }
}
