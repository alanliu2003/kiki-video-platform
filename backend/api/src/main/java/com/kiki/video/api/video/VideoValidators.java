package com.kiki.video.api.video;

import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

public final class VideoValidators {

    public static final int TITLE_MIN = 1;
    public static final int TITLE_MAX = 120;
    public static final int DESCRIPTION_MAX = 2000;
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("video/mp4", "video/webm");

    private VideoValidators() {
    }

    public static String validateTitle(String title) {
        if (title == null) {
            throw new ApiException(ErrorCode.INVALID_VIDEO_TITLE, HttpStatus.BAD_REQUEST, "Title is required");
        }
        String normalized = title.trim();
        if (normalized.length() < TITLE_MIN || normalized.length() > TITLE_MAX) {
            throw new ApiException(
                    ErrorCode.INVALID_VIDEO_TITLE,
                    HttpStatus.BAD_REQUEST,
                    "Title must be between 1 and 120 characters"
            );
        }
        return normalized;
    }

    public static String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > DESCRIPTION_MAX) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Description must be at most 2000 characters"
            );
        }
        return normalized;
    }

    public static String validateContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new ApiException(
                    ErrorCode.UNSUPPORTED_VIDEO_TYPE,
                    HttpStatus.BAD_REQUEST,
                    "Only video/mp4 and video/webm uploads are supported"
            );
        }
        return normalized;
    }

    public static String safeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "video";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "video";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
