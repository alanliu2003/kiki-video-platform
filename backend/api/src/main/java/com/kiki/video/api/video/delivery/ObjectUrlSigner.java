package com.kiki.video.api.video.delivery;

import java.time.Duration;

/**
 * Signs GET access for a trusted object key. Callers must already have resolved
 * the key from database metadata — never from user-supplied object paths.
 */
public interface ObjectUrlSigner {

    String presignGet(String objectKey, Duration ttl);
}
