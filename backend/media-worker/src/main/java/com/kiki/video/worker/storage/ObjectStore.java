package com.kiki.video.worker.storage;

import java.nio.file.Path;
import java.util.List;

public interface ObjectStore {

    void ensureReady();

    void downloadTo(String objectKey, Path destination);

    void putFile(String objectKey, Path file, String contentType);

    void copy(String sourceKey, String destKey);

    List<String> list(String prefix);

    void deletePrefix(String prefix);
}
