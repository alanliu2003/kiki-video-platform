package com.kiki.video.common.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenditionLadderTest {

    @Test
    void source480pGets360AndSourceIsh() {
        List<Rendition> renditions = RenditionLadder.select(854, 480);
        assertThat(renditions).extracting(Rendition::name).containsExactly("360p", "480p");
        assertThat(renditions).noneMatch(rendition -> rendition.height() > 480);
    }

    @Test
    void source720pGets360And720() {
        assertThat(RenditionLadder.select(1280, 720))
                .extracting(Rendition::name)
                .containsExactly("360p", "720p");
    }

    @Test
    void source1080pGetsFullLadderWithoutUpscale() {
        assertThat(RenditionLadder.select(1920, 1080))
                .extracting(Rendition::name)
                .containsExactly("360p", "720p", "1080p");
    }

    @Test
    void source4kDoesNotCreateHigherThan1080() {
        assertThat(RenditionLadder.select(3840, 2160))
                .extracting(Rendition::name)
                .containsExactly("360p", "720p", "1080p");
    }

    @Test
    void tinySourceKeepsNativeHeightOnly() {
        assertThat(RenditionLadder.select(426, 240))
                .extracting(Rendition::name)
                .containsExactly("240p");
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThatThrownBy(() -> RenditionLadder.select(0, 720))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
