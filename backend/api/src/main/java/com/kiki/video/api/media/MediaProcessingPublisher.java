package com.kiki.video.api.media;

import com.kiki.video.common.media.MediaProcessingRequestedEvent;

public interface MediaProcessingPublisher {

    void publishProcessingRequested(MediaProcessingRequestedEvent event);
}
