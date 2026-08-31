package com.kiki.video.api.video.service;

import com.kiki.video.api.config.VideoProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.media.MediaProcessingRequestService;
import com.kiki.video.api.search.service.SearchIndexRequestService;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.user.model.UserRole;
import com.kiki.video.api.user.model.UserStatus;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.storage.VideoStorage;
import com.kiki.video.api.video.storage.VideoStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private VideoMapper videoMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private MediaObjectMapper mediaObjectMapper;

    @Mock
    private VideoStorage videoStorage;

    @Mock
    private MediaProcessingRequestService mediaProcessingRequestService;

    @Mock
    private SearchIndexRequestService searchIndexRequestService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private VideoService videoService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        videoService = new VideoService(
                videoMapper,
                userMapper,
                mediaObjectMapper,
                videoStorage,
                new VideoProperties(
                        DataSize.ofMegabytes(250),
                        DataSize.ofGigabytes(10),
                        DataSize.ofMegabytes(8),
                        Duration.ofHours(24),
                        Duration.ofMinutes(15)
                ),
                mediaProcessingRequestService,
                searchIndexRequestService,
                transactionManager
        );
    }

    @Test
    void minioFailureReturnsSafeStorageError() {
        when(userMapper.findById(1L)).thenReturn(owner());
        doThrow(new VideoStorageException("down")).when(videoStorage)
                .put(anyString(), any(), anyLong(), anyString());

        assertThatThrownBy(() -> videoService.upload(
                1L,
                "Demo",
                "First upload",
                new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[] {1, 2, 3})
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.VIDEO_STORAGE_ERROR);
                    assertThat(api.getMessage()).doesNotContain("down");
                });

        verify(videoMapper, never()).insert(any());
    }

    @Test
    void databaseFailureAttemptsMinioCompensation() {
        when(userMapper.findById(1L)).thenReturn(owner());
        when(mediaObjectMapper.findBySha256(anyString())).thenReturn(null);
        when(mediaObjectMapper.insert(any())).thenAnswer(invocation -> {
            MediaObject media = invocation.getArgument(0);
            media.setId(9L);
            return 1;
        });
        doThrow(new RuntimeException("db down")).when(videoMapper).insert(any());

        assertThatThrownBy(() -> videoService.upload(
                1L,
                "Demo",
                null,
                new MockMultipartFile("file", "demo.mp4", "video/mp4", new byte[] {1, 2, 3})
        )).isInstanceOf(ApiException.class);

        verify(videoStorage).put(anyString(), any(), eq(3L), eq("video/mp4"));
        verify(videoStorage).delete(anyString());
    }

    private static User owner() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("alice");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
