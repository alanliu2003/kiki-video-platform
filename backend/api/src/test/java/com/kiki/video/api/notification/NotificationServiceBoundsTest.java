package com.kiki.video.api.notification.service;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceBoundsTest {

    @Test
    void defaultsAndCapsPageSize() {
        assertThat(NotificationService.bounds(null, null)).isEqualTo(new NotificationService.PageBounds(0, 20, 0));
        assertThat(NotificationService.bounds(-3, 0)).isEqualTo(new NotificationService.PageBounds(0, 20, 0));
        assertThat(NotificationService.bounds(2, 200)).isEqualTo(new NotificationService.PageBounds(2, 50, 100));
        assertThat(NotificationService.bounds(1, 10)).isEqualTo(new NotificationService.PageBounds(1, 10, 10));
    }
}
