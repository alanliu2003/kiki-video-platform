package com.kiki.video.api.video.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public record StoredVideoObject(InputStream stream, long size) implements Closeable {

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
