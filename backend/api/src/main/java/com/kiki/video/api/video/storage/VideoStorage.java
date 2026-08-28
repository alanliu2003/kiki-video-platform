package com.kiki.video.api.video.storage;

import java.io.InputStream;

public interface VideoStorage {

    void ensureReady();

    void put(String objectKey, InputStream content, long size, String contentType);

    void delete(String objectKey);

    void deletePrefix(String prefix);

    boolean exists(String objectKey);

    long size(String objectKey);

    StoredVideoObject open(String objectKey, long offset, long length);
}
