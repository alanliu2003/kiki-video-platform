package com.kiki.video.common.media;

import java.util.Optional;
import java.util.function.Function;

/**
 * Rewrites HLS playlist URI lines (and quoted URI= attributes) using a caller-supplied mapper.
 * Comment lines and tags without URIs are left unchanged.
 */
public final class HlsPlaylistRewriter {

    private HlsPlaylistRewriter() {
    }

    public static String rewrite(String playlist, Function<String, String> uriMapper) {
        if (playlist == null || playlist.isEmpty()) {
            return playlist;
        }
        String[] lines = playlist.split("\n", -1);
        StringBuilder out = new StringBuilder(playlist.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(rewriteLine(lines[i], uriMapper));
        }
        return out.toString();
    }

    /**
     * Resolves a child URI against the playlist's relative path (for example
     * {@code 360p/index.m3u8} + {@code segment000.ts} → {@code 360p/segment000.ts}).
     */
    public static Optional<String> resolveChild(String playlistRelativePath, String childUri) {
        if (childUri == null || childUri.isBlank()) {
            return Optional.empty();
        }
        String child = childUri.trim().replace('\\', '/');
        if (child.startsWith("http://") || child.startsWith("https://") || child.startsWith("/")) {
            return Optional.empty();
        }
        if (child.contains("..") || child.contains("//")) {
            return Optional.empty();
        }
        String base = playlistRelativePath == null ? "" : playlistRelativePath.replace('\\', '/');
        if (base.startsWith("/")) {
            base = base.substring(1);
        }
        int slash = base.lastIndexOf('/');
        String dir = slash < 0 ? "" : base.substring(0, slash + 1);
        return Optional.of(dir + child);
    }

    private static String rewriteLine(String line, Function<String, String> uriMapper) {
        if (line.isEmpty()) {
            return line;
        }
        if (line.charAt(0) == '#') {
            return rewriteTaggedUri(line, uriMapper);
        }
        String mapped = uriMapper.apply(line.trim());
        return mapped == null ? line : mapped;
    }

    private static String rewriteTaggedUri(String line, Function<String, String> uriMapper) {
        int uriAttr = indexOfIgnoreCase(line, "URI=\"");
        if (uriAttr < 0) {
            return line;
        }
        int valueStart = uriAttr + 5;
        int valueEnd = line.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return line;
        }
        String original = line.substring(valueStart, valueEnd);
        String mapped = uriMapper.apply(original);
        if (mapped == null || mapped.equals(original)) {
            return line;
        }
        return line.substring(0, valueStart) + mapped + line.substring(valueEnd);
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toUpperCase().indexOf(needle.toUpperCase());
    }
}
