package com.kiki.video.common.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class RenditionLadder {

    public static final int AUDIO_BITRATE_KBPS = 128;
    private static final int[] TARGET_HEIGHTS = {360, 720, 1080};

    private RenditionLadder() {
    }

    public static List<Rendition> select(int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
        List<Rendition> selected = new ArrayList<>();
        for (int height : TARGET_HEIGHTS) {
            if (sourceHeight >= height) {
                selected.add(renditionForHeight(height));
            }
        }
        boolean exactTarget = Set.of(360, 720, 1080).contains(sourceHeight);
        if (selected.isEmpty() || (!exactTarget && sourceHeight < 1080)) {
            selected.add(renditionForHeight(sourceHeight));
        }
        selected.sort(Comparator.comparingInt(Rendition::height));
        return List.copyOf(selected);
    }

    static Rendition renditionForHeight(int height) {
        int videoBitrate = videoBitrateKbps(height);
        return new Rendition(height + "p", height, videoBitrate, AUDIO_BITRATE_KBPS);
    }

    static int videoBitrateKbps(int height) {
        if (height <= 360) {
            return 800;
        }
        if (height <= 720) {
            return 2500;
        }
        return 5000;
    }
}
