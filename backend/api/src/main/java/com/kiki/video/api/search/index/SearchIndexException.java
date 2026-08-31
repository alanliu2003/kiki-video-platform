package com.kiki.video.api.search.index;

public class SearchIndexException extends RuntimeException {

    public SearchIndexException(String message) {
        super(message);
    }

    public SearchIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
