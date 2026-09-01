package com.kiki.video.api.observability.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

final class DependencyHealth {

    static final Status DEGRADED = new Status("DEGRADED");

    private DependencyHealth() {
    }

    static Health up(String component) {
        return Health.up().withDetail("component", component).withDetail("available", true).build();
    }

    static Health disabled(String component) {
        return Health.up()
                .withDetail("component", component)
                .withDetail("available", false)
                .withDetail("enabled", false)
                .build();
    }

    static Health degraded(String component, String reason) {
        return Health.status(DEGRADED)
                .withDetail("component", component)
                .withDetail("available", false)
                .withDetail("reason", truncate(reason))
                .build();
    }

    static Health down(String component, String reason) {
        return Health.down()
                .withDetail("component", component)
                .withDetail("available", false)
                .withDetail("reason", truncate(reason))
                .build();
    }

    static String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unavailable";
        }
        String trimmed = reason.strip();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }
}
