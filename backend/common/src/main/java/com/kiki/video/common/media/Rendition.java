package com.kiki.video.common.media;

public record Rendition(
        String name,
        int height,
        int videoBitrateKbps,
        int audioBitrateKbps
) {

    public int bandwidthBps() {
        return (videoBitrateKbps + audioBitrateKbps) * 1000;
    }

    public String resolution(int sourceWidth, int sourceHeight) {
        int width = even(Math.max(2, Math.round(sourceWidth * (height / (float) sourceHeight))));
        return width + "x" + height;
    }

    private static int even(int value) {
        return value % 2 == 0 ? value : value + 1;
    }
}
