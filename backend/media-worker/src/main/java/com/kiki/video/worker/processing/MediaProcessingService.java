package com.kiki.video.worker.processing;

import com.kiki.video.common.media.HlsAssetPaths;
import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import com.kiki.video.common.media.MediaProcessingStatus;
import com.kiki.video.common.media.ProcessedObjectKeys;
import com.kiki.video.common.media.ProcessingDiagnostics;
import com.kiki.video.common.media.Rendition;
import com.kiki.video.common.media.RenditionLadder;
import com.kiki.video.worker.config.WorkerMediaProperties;
import com.kiki.video.worker.ffmpeg.FfmpegCommands;
import com.kiki.video.worker.ffmpeg.FfprobeParser;
import com.kiki.video.worker.ffmpeg.MasterPlaylistWriter;
import com.kiki.video.worker.ffmpeg.ProcessResult;
import com.kiki.video.worker.ffmpeg.ProcessRunner;
import com.kiki.video.worker.ffmpeg.SourceMetadata;
import com.kiki.video.worker.mapper.MediaProcessingMapper;
import com.kiki.video.worker.model.ProcessingMediaObject;
import com.kiki.video.worker.storage.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);

    private final MediaProcessingMapper mapper;
    private final ObjectStore objectStore;
    private final ProcessRunner processRunner;
    private final WorkerMediaProperties properties;
    private final ObjectMapper objectMapper;

    public MediaProcessingService(
            MediaProcessingMapper mapper,
            ObjectStore objectStore,
            ProcessRunner processRunner,
            WorkerMediaProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectStore = objectStore;
        this.processRunner = processRunner;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void handle(MediaProcessingRequestedEvent event) {
        ProcessingMediaObject current = mapper.findById(event.mediaObjectId());
        if (current == null) {
            log.warn("media processing skipped missing mediaObjectId={}", event.mediaObjectId());
            return;
        }
        if (current.getProcessingStatus() == MediaProcessingStatus.READY) {
            log.info("media processing skipped already ready mediaObjectId={}", current.getId());
            return;
        }
        Instant now = Instant.now();
        int claimed = mapper.claim(
                current.getId(),
                properties.maxAttempts(),
                now.minus(properties.staleProcessingAfter()),
                now
        );
        if (claimed == 0) {
            log.info(
                    "media processing skipped unclaimed mediaObjectId={} status={}",
                    current.getId(),
                    current.getProcessingStatus()
            );
            return;
        }
        log.info("media processing claimed mediaObjectId={} sha256={}", current.getId(), current.getSha256());
        processClaimed(mapper.findById(current.getId()));
    }

    void processClaimed(ProcessingMediaObject media) {
        Path workspace = properties.tempDirectory().resolve("job-" + media.getId() + "-" + UUID.randomUUID());
        Instant started = Instant.now();
        try {
            Files.createDirectories(workspace);
            Path source = workspace.resolve("source.bin");
            objectStore.downloadTo(media.getObjectKey(), source);
            SourceMetadata metadata = probe(source);
            log.info(
                    "ffprobe complete mediaObjectId={} durationSeconds={} width={} height={} videoCodec={} audioCodec={}",
                    media.getId(),
                    metadata.durationSeconds(),
                    metadata.width(),
                    metadata.height(),
                    metadata.videoCodec(),
                    metadata.audioCodec()
            );
            List<Rendition> renditions = RenditionLadder.select(metadata.width(), metadata.height());
            Path output = workspace.resolve("hls");
            Files.createDirectories(output);
            for (Rendition rendition : renditions) {
                Path renditionDir = output.resolve(rendition.name());
                Files.createDirectories(renditionDir);
                ProcessResult result = processRunner.run(
                        FfmpegCommands.rendition(
                                properties.ffmpegPath(),
                                source,
                                renditionDir,
                                rendition,
                                metadata,
                                properties.hlsSegmentDuration()
                        ),
                        properties.timeout(),
                        renditionDir
                );
                if (!result.succeeded()) {
                    throw new IllegalStateException(failureMessage("ffmpeg", result));
                }
                if (!Files.exists(renditionDir.resolve("index.m3u8"))) {
                    throw new IllegalStateException("FFmpeg did not produce a rendition playlist");
                }
            }
            log.info(
                    "ffmpeg complete mediaObjectId={} renditions={}",
                    media.getId(),
                    renditions.stream().map(Rendition::name).toList()
            );
            Path thumbnail = output.resolve("thumbnail.jpg");
            ProcessResult thumbResult = processRunner.run(
                    FfmpegCommands.thumbnail(
                            properties.ffmpegPath(),
                            source,
                            thumbnail,
                            FfmpegCommands.thumbnailOffset(metadata.durationSeconds())
                    ),
                    properties.timeout(),
                    output
            );
            if (!thumbResult.succeeded() || !Files.exists(thumbnail)) {
                throw new IllegalStateException(failureMessage("thumbnail", thumbResult));
            }
            MasterPlaylistWriter.write(output.resolve("master.m3u8"), renditions, metadata.width(), metadata.height());
            uploadProcessed(media.getId(), output);
            Instant finished = Instant.now();
            mapper.markReady(
                    media.getId(),
                    ProcessedObjectKeys.prefix(media.getId()),
                    ProcessedObjectKeys.master(media.getId()),
                    ProcessedObjectKeys.thumbnail(media.getId()),
                    metadata.durationSeconds(),
                    metadata.width(),
                    metadata.height(),
                    finished
            );
            log.info(
                    "media ready mediaObjectId={} sourceSizeBytes={} sourceDurationSeconds={} processingDurationMs={} renditions={}",
                    media.getId(),
                    media.getFileSizeBytes(),
                    metadata.durationSeconds(),
                    Duration.between(started, finished).toMillis(),
                    renditions.stream().map(Rendition::name).toList()
            );
        } catch (RuntimeException | IOException ex) {
            fail(media, started, ex);
        } finally {
            deleteRecursive(workspace);
        }
    }

    private SourceMetadata probe(Path source) {
        ProcessResult result = processRunner.run(
                FfmpegCommands.ffprobe(properties.ffprobePath(), source),
                Duration.ofMinutes(2),
                source.getParent()
        );
        if (!result.succeeded()) {
            throw new IllegalStateException(failureMessage("ffprobe", result));
        }
        return FfprobeParser.parse(result.stdout());
    }

    private void uploadProcessed(long mediaObjectId, Path output) throws IOException {
        objectStore.deletePrefix(ProcessedObjectKeys.prefix(mediaObjectId));
        String stagingPrefix = ProcessedObjectKeys.stagingPrefix(mediaObjectId);
        try (Stream<Path> files = Files.walk(output)) {
            List<Path> uploaded = files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path file : uploaded) {
                String relative = output.relativize(file).toString().replace('\\', '/');
                String stagingKey = stagingPrefix + relative;
                objectStore.putFile(stagingKey, file, HlsAssetPaths.contentType(relative));
            }
        }
        List<String> stagingKeys = objectStore.list(stagingPrefix);
        if (stagingKeys.stream().noneMatch(key -> key.endsWith("master.m3u8"))
                || stagingKeys.stream().noneMatch(key -> key.endsWith("thumbnail.jpg"))) {
            throw new IllegalStateException("Staging upload is missing master playlist or thumbnail");
        }
        for (String stagingKey : stagingKeys) {
            objectStore.copy(stagingKey, ProcessedObjectKeys.finalKey(mediaObjectId, stagingKey));
        }
        objectStore.deletePrefix(stagingPrefix);
        log.info("assets uploaded mediaObjectId={} files={}", mediaObjectId, stagingKeys.size());
    }

    private void fail(ProcessingMediaObject media, Instant started, Exception ex) {
        String diagnostic = ProcessingDiagnostics.truncate(ex.getMessage());
        mapper.markFailed(media.getId(), diagnostic, Instant.now());
        ProcessingMediaObject updated = mapper.findById(media.getId());
        log.warn(
                "processing failed mediaObjectId={} processingDurationMs={} error={}",
                media.getId(),
                Duration.between(started, Instant.now()).toMillis(),
                diagnostic
        );
        try {
            objectStore.deletePrefix(ProcessedObjectKeys.prefix(media.getId()));
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to delete incomplete processed prefix mediaObjectId={}", media.getId(), cleanupEx);
        }
        if (updated != null && updated.getProcessingAttempts() < properties.maxAttempts()) {
            MediaProcessingRequestedEvent retry = MediaProcessingRequestedEvent.create(
                    media.getId(),
                    media.getSha256(),
                    media.getObjectKey()
            );
            Instant now = Instant.now();
            try {
                mapper.insertRetryOutbox(
                        media.getId(),
                        MediaProcessingRequestedEvent.EVENT_TYPE,
                        retry.eventVersion(),
                        objectMapper.writeValueAsString(retry),
                        now.plus(properties.retryBackoff().multipliedBy(updated.getProcessingAttempts())),
                        now
                );
            } catch (DataIntegrityViolationException ignored) {
                // An unpublished outbox row already exists.
            }
        }
    }

    private static String failureMessage(String stage, ProcessResult result) {
        if (result.timedOut()) {
            return stage + " timed out";
        }
        return stage + " failed with exit code " + result.exitCode();
    }

    static void deleteRecursive(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> files = Files.walk(workspace)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of the per-job workspace.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup of the per-job workspace.
        }
    }
}
