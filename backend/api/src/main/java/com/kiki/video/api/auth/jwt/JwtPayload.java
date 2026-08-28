package com.kiki.video.api.auth.jwt;

public record JwtPayload(Long userId, String role) {
}
