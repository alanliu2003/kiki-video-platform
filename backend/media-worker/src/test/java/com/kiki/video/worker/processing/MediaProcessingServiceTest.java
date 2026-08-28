package com.kiki.video.worker.processing;

import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import com.kiki.video.common.media.MediaProcessingStatus;
import com.kiki.video.common.media.ProcessedObjectKeys;
import com.kiki.video.worker.config.WorkerMediaProperties;
import com.kiki.video.worker.ffmpeg.ProcessResult;
import com.kiki.video.worker.ffmpeg.ProcessRunner;
import com.kiki.video.worker.mapper.MediaProcessingMapper;
import com.kiki.video.worker.model.ProcessingMediaObject;
import com.kiki.video.worker.storage.ObjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaProcessingServiceTest {

    @Mock
    private MediaProcessingMapper mapper;

    @Mock
    private ObjectStore objectStore;

    @Mock
    private ProcessRunner processRunner;

    @TempDir
    Path tempDir;

    private MediaProcessingService service;
    private final List<String> uploaded = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MediaProcessingService(
                mapper,
                objectStore,
                processRunner,
                new WorkerMediaProperties(
                        "ffmpeg",
                        "ffprobe",
                        tempDir.toString(),
                        Duration.ofMinutes(2),
                        6,
                        3,
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(1),
                        false
                ),
                new ObjectMapper()
        );
    }

    @Test
    void successfulProcessingUploadsAssetsAndMarksReady() throws Exception {
        ProcessingMediaObject media = media(MediaProcessingStatus.PENDING);
        when(mapper.findById(7L)).thenReturn(media);
        when(mapper.claim(eq(7L), eq(3), any(), any())).thenReturn(1);
        when(processRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            List<String> command = invocation.getArgument(0);
            Path workDir = invocation.getArgument(2);
            if (command.contains("-of")) {
                return new ProcessResult(0, false, """
                        {"streams":[{"codec_type":"video","codec_name":"h264","width":1280,"height":720}],"format":{"duration":"2.0"}}
                        """, "");
            }
            if (command.contains("-frames:v")) {
                Files.writeString(workDir.resolve("thumbnail.jpg"), "thumb");
                return new ProcessResult(0, false, "", "");
            }
            Files.writeString(workDir.resolve("index.m3u8"), "#EXTM3U\n");
            Files.writeString(workDir.resolve("segment000.ts"), "ts");
            return new ProcessResult(0, false, "", "");
        });
        when(objectStore.list(anyString())).thenAnswer(invocation -> List.copyOf(uploaded));
        org.mockito.Mockito.doAnswer(invocation -> {
            uploaded.add(invocation.getArgument(0));
            return null;
        }).when(objectStore).putFile(anyString(), any(), anyString());

        service.handle(MediaProcessingRequestedEvent.create(7L, media.getSha256(), media.getObjectKey()));

        verify(mapper).markReady(
                eq(7L),
                eq(ProcessedObjectKeys.prefix(7L)),
                eq(ProcessedObjectKeys.master(7L)),
                eq(ProcessedObjectKeys.thumbnail(7L)),
                eq(2.0),
                eq(1280),
                eq(720),
                any()
        );
        assertThat(uploaded).anyMatch(key -> key.endsWith("master.m3u8"));
        assertThat(uploaded).anyMatch(key -> key.endsWith("thumbnail.jpg"));
        assertThat(Files.exists(tempDir) && Files.list(tempDir).findAny().isEmpty() || Files.notExists(tempDir.resolve("job-7"))).isTrue();
    }

    @Test
    void ffmpegFailureMarksFailedAndCleansWorkspace() {
        ProcessingMediaObject media = media(MediaProcessingStatus.PENDING);
        when(mapper.findById(7L)).thenReturn(media);
        when(mapper.claim(eq(7L), eq(3), any(), any())).thenReturn(1);
        when(processRunner.run(any(), any(), any())).thenReturn(new ProcessResult(1, false, "", "huge ffmpeg log ".repeat(80)));

        service.handle(MediaProcessingRequestedEvent.create(7L, media.getSha256(), media.getObjectKey()));

        verify(mapper).markFailed(eq(7L), org.mockito.ArgumentMatchers.argThat(error ->
                error.length() <= 500 && !error.contains("huge ffmpeg log ".repeat(10))
        ), any());
        verify(mapper, never()).markReady(anyLong(), anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyInt(), any());
    }

    @Test
    void readyMediaIsNotReprocessed() {
        when(mapper.findById(7L)).thenReturn(media(MediaProcessingStatus.READY));

        service.handle(MediaProcessingRequestedEvent.create(7L, "d".repeat(64), "raw/" + "d".repeat(64)));

        verify(mapper, never()).claim(anyLong(), anyInt(), any(), any());
        verify(processRunner, never()).run(any(), any(), any());
    }

    private static ProcessingMediaObject media(MediaProcessingStatus status) {
        ProcessingMediaObject media = new ProcessingMediaObject();
        media.setId(7L);
        media.setSha256("d".repeat(64));
        media.setObjectKey("raw/" + "d".repeat(64));
        media.setFileSizeBytes(2048);
        media.setProcessingStatus(status);
        media.setProcessingAttempts(0);
        return media;
    }
}
