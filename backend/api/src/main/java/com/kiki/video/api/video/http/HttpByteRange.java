package com.kiki.video.api.video.http;

import java.util.Optional;

public record HttpByteRange(long start, long endInclusive) {

    public long length() {
        return endInclusive - start + 1;
    }

    public String contentRange(long totalSize) {
        return "bytes " + start + "-" + endInclusive + "/" + totalSize;
    }

    public static Optional<HttpByteRange> parse(String header, long contentLength) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        if (contentLength <= 0) {
            throw new UnsatisfiableRangeException(contentLength);
        }

        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, "bytes=".length())) {
            throw new MalformedRangeException();
        }

        String spec = value.substring("bytes=".length()).trim();
        if (spec.isEmpty() || spec.contains(",")) {
            throw new MalformedRangeException();
        }

        int dash = spec.indexOf('-');
        if (dash < 0) {
            throw new MalformedRangeException();
        }

        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();

        try {
            if (startText.isEmpty()) {
                if (endText.isEmpty()) {
                    throw new MalformedRangeException();
                }
                long suffix = Long.parseLong(endText);
                if (suffix <= 0) {
                    throw new MalformedRangeException();
                }
                long start = Math.max(0, contentLength - suffix);
                return Optional.of(new HttpByteRange(start, contentLength - 1));
            }

            long start = Long.parseLong(startText);
            if (start < 0 || start >= contentLength) {
                throw new UnsatisfiableRangeException(contentLength);
            }

            long end = endText.isEmpty() ? contentLength - 1 : Long.parseLong(endText);
            if (end < start) {
                throw new MalformedRangeException();
            }
            end = Math.min(end, contentLength - 1);
            return Optional.of(new HttpByteRange(start, end));
        } catch (NumberFormatException ex) {
            throw new MalformedRangeException();
        }
    }

    public static final class MalformedRangeException extends RuntimeException {
        public MalformedRangeException() {
            super("Range header is invalid");
        }
    }

    public static final class UnsatisfiableRangeException extends RuntimeException {
        private final long totalSize;

        public UnsatisfiableRangeException(long totalSize) {
            super("Requested range is not satisfiable");
            this.totalSize = totalSize;
        }

        public long totalSize() {
            return totalSize;
        }
    }
}
