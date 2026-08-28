package com.kiki.video.worker.ffmpeg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfprobeParserTest {

    @Test
    void parsesDurationResolutionAndCodecs() {
        SourceMetadata metadata = FfprobeParser.parse("""
                {
                  "streams": [
                    {"codec_type":"video","codec_name":"h264","width":1280,"height":720,"duration":"2.0"},
                    {"codec_type":"audio","codec_name":"aac"}
                  ],
                  "format": {"duration":"2.040"}
                }
                """);

        assertThat(metadata.durationSeconds()).isEqualTo(2.04);
        assertThat(metadata.width()).isEqualTo(1280);
        assertThat(metadata.height()).isEqualTo(720);
        assertThat(metadata.videoCodec()).isEqualTo("h264");
        assertThat(metadata.audioCodec()).isEqualTo("aac");
        assertThat(metadata.hasAudio()).isTrue();
    }

    @Test
    void rejectsMissingVideoStream() {
        assertThatThrownBy(() -> FfprobeParser.parse("""
                {"streams":[{"codec_type":"audio","codec_name":"aac"}],"format":{"duration":"1.0"}}
                """))
                .isInstanceOf(IllegalStateException.class);
    }
}
