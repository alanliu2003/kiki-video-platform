package com.kiki.video.api.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestId {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    public static final int MAX_LENGTH = 128;

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8," + MAX_LENGTH + "}$");

    private RequestId() {
    }

    public static String resolve(String incoming) {
        if (isValid(incoming)) {
            return incoming.trim();
        }
        return generate();
    }

    public static boolean isValid(String incoming) {
        if (incoming == null) {
            return false;
        }
        String value = incoming.trim();
        if (value.length() < 8 || value.length() > MAX_LENGTH) {
            return false;
        }
        return VALID.matcher(value).matches();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? null : value;
    }
}
