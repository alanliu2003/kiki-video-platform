package com.kiki.video.api.video.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface VideoStorage {

    void ensureReady();

    void put(String objectKey, InputStream content, long size, String contentType);

    void putFile(String objectKey, Path file, String contentType);

    void downloadTo(String objectKey, Path destination);

    void copy(String sourceKey, String destKey);

    List<String> list(String prefix);

    void delete(String objectKey);

    void deletePrefix(String prefix);

    boolean exists(String objectKey);

    long size(String objectKey);

    StoredVideoObject open(String objectKey, long offset, long length);
}
