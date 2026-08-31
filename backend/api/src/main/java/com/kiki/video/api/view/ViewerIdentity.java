package com.kiki.video.api.view;

import java.util.Optional;
import java.util.UUID;

public record ViewerIdentity(String viewerKey, Optional<UUID> issuedAnonId) {

    public static final String ANON_COOKIE = "kiki_anon";

    public static ViewerIdentity authenticated(long userId) {
        return new ViewerIdentity("u:" + userId, Optional.empty());
    }

    public static ViewerIdentity anonymous(UUID anonId, boolean issuedNow) {
        return new ViewerIdentity("a:" + anonId, issuedNow ? Optional.of(anonId) : Optional.empty());
    }

    public static Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
