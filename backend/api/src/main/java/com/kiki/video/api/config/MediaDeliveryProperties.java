package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.media-delivery")
public record MediaDeliveryProperties(
        String mode,
        Duration urlTtl,
        List<String> corsOrigins
) {
    public MediaDeliveryProperties {
        if (urlTtl == null || urlTtl.isZero() || urlTtl.isNegative()) {
            urlTtl = Duration.ofMinutes(15);
        }
        corsOrigins = splitOrigins(corsOrigins);
    }

    static List<String> splitOrigins(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            for (String part : item.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        }
        return List.copyOf(out);
    }

    public MediaDeliveryMode deliveryMode() {
        return MediaDeliveryMode.from(mode);
    }

    public List<String> effectiveCorsOrigins() {
        if (!corsOrigins.isEmpty()) {
            return corsOrigins;
        }
        List<String> defaults = new ArrayList<>();
        defaults.add("http://localhost:5173");
        defaults.add("http://127.0.0.1:5173");
        defaults.add("http://localhost:8088");
        defaults.add("http://127.0.0.1:8088");
        return List.copyOf(defaults);
    }
}
