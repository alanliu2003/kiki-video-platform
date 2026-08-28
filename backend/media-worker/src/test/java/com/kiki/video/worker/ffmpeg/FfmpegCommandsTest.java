package com.kiki.video.worker.ffmpeg;

import com.kiki.video.common.media.RenditionLadder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegCommandsTest {

    @TempDir
    Path tempDir;

    @Test
    void renditionCommandUsesArgumentArrayAndDoesNotShellInterpolate() {
        Path source = tempDir.resolve("source.bin");
        Path output = tempDir.resolve("360p");
        List<String> command = FfmpegCommands.rendition(
                "ffmpeg",
                source,
                output,
                RenditionLadder.select(1280, 720).getFirst(),
                new SourceMetadata(4, 1280, 720, "h264", "aac"),
                6
        );

        assertThat(command.getFirst()).isEqualTo("ffmpeg");
        assertThat(command).doesNotContainAnyElementsOf(List.of("&&", "|", ";"));
        assertThat(String.join(" ", command)).doesNotContain("cmd.exe");
        assertThat(command).contains("-hls_time", "6", "-hls_playlist_type", "vod", "scale=-2:360");
    }

    @Test
    void thumbnailOffsetStaysInsideShortVideos() {
        assertThat(FfmpegCommands.thumbnailOffset(1.0)).isEqualTo(0.1);
        assertThat(FfmpegCommands.thumbnailOffset(20.0)).isEqualTo(2.0);
    }
}
