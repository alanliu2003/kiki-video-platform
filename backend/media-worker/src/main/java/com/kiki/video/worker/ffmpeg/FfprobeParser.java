package com.kiki.video.worker.ffmpeg;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class FfprobeParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FfprobeParser() {
    }

    public static SourceMetadata parse(String json) {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode format = root.path("format");
        double duration = format.path("duration").asDouble(0);
        int width = 0;
        int height = 0;
        String videoCodec = null;
        String audioCodec = null;
        for (JsonNode stream : root.path("streams")) {
            String type = text(stream, "codec_type");
            if (type == null) {
                type = "";
            }
            if ("video".equals(type) && videoCodec == null) {
                width = stream.path("width").asInt(0);
                height = stream.path("height").asInt(0);
                videoCodec = text(stream, "codec_name");
                if (duration <= 0) {
                    duration = stream.path("duration").asDouble(0);
                }
            } else if ("audio".equals(type) && audioCodec == null) {
                audioCodec = text(stream, "codec_name");
            }
        }
        if (width <= 0 || height <= 0 || duration <= 0 || videoCodec == null) {
            throw new IllegalStateException("ffprobe did not return usable video metadata");
        }
        return new SourceMetadata(duration, width, height, videoCodec, audioCodec);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }
}
