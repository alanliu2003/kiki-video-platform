package com.kiki.video.api.upload.service;

import com.kiki.video.api.config.VideoProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.media.MediaProcessingRequestService;
import com.kiki.video.api.upload.UploadMath;
import com.kiki.video.api.upload.UploadObjectKeys;
import com.kiki.video.api.upload.dto.CompleteUploadRequest;
import com.kiki.video.api.upload.dto.CompleteUploadResponse;
import com.kiki.video.api.upload.dto.InitUploadRequest;
import com.kiki.video.api.upload.dto.InitUploadResponse;
import com.kiki.video.api.upload.dto.UploadStatusResponse;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.mapper.UploadChunkMapper;
import com.kiki.video.api.upload.mapper.UploadSessionMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.upload.model.UploadChunk;
import com.kiki.video.api.upload.model.UploadSession;
import com.kiki.video.api.upload.model.UploadSessionStatus;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.VideoValidators;
import com.kiki.video.api.video.dto.VideoResponse;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.api.video.model.VideoStatus;
import com.kiki.video.api.video.storage.VideoStorage;
import com.kiki.video.api.video.storage.VideoStorageException;
import com.kiki.video.common.media.MediaProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadSessionMapper uploadSessionMapper;
    private final UploadChunkMapper uploadChunkMapper;
    private final MediaObjectMapper mediaObjectMapper;
    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final VideoStorage videoStorage;
    private final VideoProperties videoProperties;
    private final MediaProcessingRequestService mediaProcessingRequestService;
    private final TransactionTemplate transactionTemplate;

    public UploadService(
            UploadSessionMapper uploadSessionMapper,
            UploadChunkMapper uploadChunkMapper,
            MediaObjectMapper mediaObjectMapper,
            VideoMapper videoMapper,
            UserMapper userMapper,
            VideoStorage videoStorage,
            VideoProperties videoProperties,
            MediaProcessingRequestService mediaProcessingRequestService,
            PlatformTransactionManager transactionManager
    ) {
        this.uploadSessionMapper = uploadSessionMapper;
        this.uploadChunkMapper = uploadChunkMapper;
        this.mediaObjectMapper = mediaObjectMapper;
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.videoStorage = videoStorage;
        this.videoProperties = videoProperties;
        this.mediaProcessingRequestService = mediaProcessingRequestService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public InitUploadResponse init(Long userId, InitUploadRequest request) {
        requireUser(userId);
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Request body is invalid");
        }
        String fileSha256 = validateSha256(request.fileSha256());
        long fileSizeBytes = validateFileSize(request.fileSizeBytes());
        String contentType = VideoValidators.validateContentType(request.contentType());
        String fileName = VideoValidators.safeOriginalFilename(request.fileName());

        MediaObject media = mediaObjectMapper.findBySha256(fileSha256);
        if (media != null && media.getFileSizeBytes() != fileSizeBytes) {
            throw new ApiException(
                    ErrorCode.UPLOAD_HASH_MISMATCH,
                    HttpStatus.BAD_REQUEST,
                    "Declared file size does not match the existing file"
            );
        }

        UploadSession existing = uploadSessionMapper.findActiveByUserHashAndSize(userId, fileSha256, fileSizeBytes);
        Instant now = Instant.now();
        if (existing != null && isExpired(existing, now)) {
            expire(existing);
            existing = null;
        }
        if (existing != null) {
            if (media != null && !existing.isDeduplicated()) {
                uploadSessionMapper.updateDeduplicated(existing.getId(), true, now);
                existing.setDeduplicated(true);
            }
            return toInitResponse(existing, media);
        }

        UploadSession session = new UploadSession();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setFileName(fileName);
        session.setFileSizeBytes(fileSizeBytes);
        session.setFileSha256(fileSha256);
        session.setContentType(contentType);
        session.setChunkSizeBytes(videoProperties.chunkSizeBytes());
        session.setTotalChunks(UploadMath.totalChunks(fileSizeBytes, session.getChunkSizeBytes()));
        session.setStatus(UploadSessionStatus.INITIATED);
        session.setDeduplicated(media != null);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(videoProperties.sessionTtl()));

        try {
            uploadSessionMapper.insert(session);
        } catch (DataIntegrityViolationException ex) {
            UploadSession raced = uploadSessionMapper.findActiveByUserHashAndSize(userId, fileSha256, fileSizeBytes);
            if (raced == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create upload session");
            }
            return toInitResponse(raced, media);
        }
        return toInitResponse(session, media);
    }

    public UploadStatusResponse status(Long userId, UUID uploadId) {
        UploadSession session = requireOwnedSession(userId, uploadId);
        MediaObject media = mediaObjectMapper.findBySha256(session.getFileSha256());
        List<Integer> uploaded = uploadChunkMapper.findIndexes(session.getId());
        List<Integer> missing = missingChunks(session.getTotalChunks(), uploaded);
        boolean uploadRequired = media == null && !session.isDeduplicated();
        return new UploadStatusResponse(
                session.getId(),
                session.getStatus().name(),
                session.getTotalChunks(),
                uploaded,
                missing,
                session.getExpiresAt(),
                media != null || session.isDeduplicated(),
                uploadRequired
        );
    }

    public void uploadChunk(Long userId, UUID uploadId, int chunkIndex, InputStream body, Long contentLength) {
        UploadSession session = requireOwnedSession(userId, uploadId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED
                || session.getStatus() == UploadSessionStatus.COMPLETING
                || session.getStatus() == UploadSessionStatus.FAILED) {
            throw new ApiException(
                    ErrorCode.UPLOAD_INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Upload session cannot accept more chunks"
            );
        }
        if (session.isDeduplicated() || mediaObjectMapper.findBySha256(session.getFileSha256()) != null) {
            drain(body);
            return;
        }
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new ApiException(
                    ErrorCode.UPLOAD_CHUNK_OUT_OF_RANGE,
                    HttpStatus.BAD_REQUEST,
                    "Chunk index is out of range"
            );
        }
        long expectedSize = UploadMath.expectedChunkSize(session.getFileSizeBytes(), session.getChunkSizeBytes(), chunkIndex);
        if (contentLength != null && contentLength != expectedSize) {
            throw new ApiException(
                    ErrorCode.UPLOAD_CHUNK_SIZE_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "Chunk size does not match the expected size"
            );
        }

        UploadChunk existing = uploadChunkMapper.find(session.getId(), chunkIndex);
        if (existing != null) {
            drain(body);
            return;
        }

        MessageDigest digest = sha256Digest();
        String objectKey = UploadObjectKeys.chunk(session.getId(), chunkIndex);
        try {
            videoStorage.put(objectKey, new DigestInputStream(body, digest), expectedSize, "application/octet-stream");
        } catch (VideoStorageException ex) {
            throw storageError();
        }

        try {
            if (videoStorage.size(objectKey) != expectedSize) {
                safeDelete(objectKey);
                throw new ApiException(
                        ErrorCode.UPLOAD_CHUNK_SIZE_INVALID,
                        HttpStatus.BAD_REQUEST,
                        "Chunk size does not match the expected size"
                );
            }
        } catch (VideoStorageException ex) {
            throw storageError();
        }

        UploadChunk chunk = new UploadChunk();
        chunk.setUploadSessionId(session.getId());
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkSizeBytes(expectedSize);
        chunk.setChunkSha256(HexFormat.of().formatHex(digest.digest()));
        chunk.setCreatedAt(Instant.now());
        try {
            uploadChunkMapper.insert(chunk);
        } catch (DataIntegrityViolationException ex) {
            return;
        }

        if (session.getStatus() == UploadSessionStatus.INITIATED) {
            uploadSessionMapper.updateStatus(session.getId(), UploadSessionStatus.UPLOADING, Instant.now());
        }
    }

    public CompleteUploadResponse complete(Long userId, UUID uploadId, CompleteUploadRequest request) {
        String title = VideoValidators.validateTitle(request == null ? null : request.title());
        String description = VideoValidators.validateDescription(request == null ? null : request.description());

        UploadSession claimed = transactionTemplate.execute(status -> claimForComplete(userId, uploadId));
        if (claimed.getStatus() == UploadSessionStatus.COMPLETED) {
            return completedResponse(claimed, true);
        }

        MediaObject media = mediaObjectMapper.findBySha256(claimed.getFileSha256());
        boolean reusedPhysical = media != null;
        if (media == null) {
            List<Integer> uploaded = uploadChunkMapper.findIndexes(claimed.getId());
            if (!allChunksPresent(uploaded, claimed.getTotalChunks())) {
                uploadSessionMapper.updateStatus(claimed.getId(), UploadSessionStatus.UPLOADING, Instant.now());
                throw new ApiException(
                        ErrorCode.UPLOAD_INCOMPLETE,
                        HttpStatus.BAD_REQUEST,
                        "Not all chunks have been uploaded"
                );
            }
            assembleAndValidate(claimed);
            media = persistMedia(claimed);
        }

        MediaObject finalizedMedia = media;
        CompleteUploadResponse response = transactionTemplate.execute(status -> {
            UploadSession locked = uploadSessionMapper.findByIdForUpdate(uploadId);
            if (locked == null || !locked.getUserId().equals(userId)) {
                throw notFound();
            }
            if (locked.getStatus() == UploadSessionStatus.COMPLETED && locked.getFinalVideoId() != null) {
                return completedResponse(locked, true);
            }
            User owner = requireUser(userId);
            Video video = insertVideo(locked, finalizedMedia, title, description);
            mediaProcessingRequestService.requestIfNeeded(finalizedMedia);
            uploadSessionMapper.markCompleted(locked.getId(), video.getId(), Instant.now());
            MediaObject currentMedia = mediaObjectMapper.findById(finalizedMedia.getId());
            if (currentMedia != null) {
                video.setProcessingStatus(currentMedia.getProcessingStatus());
            }
            return new CompleteUploadResponse(VideoResponse.from(video, owner), reusedPhysical || locked.isDeduplicated());
        });

        deleteTemporaryChunks(claimed.getId());
        return response;
    }

    public int cleanupExpiredSessions() {
        Instant now = Instant.now();
        int cleaned = 0;
        for (UploadSession session : uploadSessionMapper.findExpired(now)) {
            try {
                expire(session);
                cleaned++;
            } catch (RuntimeException ex) {
                log.warn("Failed to clean up expired upload session {}", session.getId(), ex);
            }
        }
        return cleaned;
    }

    private UploadSession claimForComplete(Long userId, UUID uploadId) {
        UploadSession session = uploadSessionMapper.findByIdForUpdate(uploadId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw notFound();
        }
        Instant now = Instant.now();
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            return session;
        }
        if (session.getStatus() == UploadSessionStatus.EXPIRED || isExpired(session, now)) {
            expire(session);
            throw expired();
        }
        if (session.getStatus() == UploadSessionStatus.FAILED) {
            throw new ApiException(
                    ErrorCode.UPLOAD_INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Upload session cannot be completed"
            );
        }
        MediaObject media = mediaObjectMapper.findBySha256(session.getFileSha256());
        if (media == null && !session.isDeduplicated()) {
            List<Integer> uploaded = uploadChunkMapper.findIndexes(session.getId());
            if (!allChunksPresent(uploaded, session.getTotalChunks())) {
                throw new ApiException(
                        ErrorCode.UPLOAD_INCOMPLETE,
                        HttpStatus.BAD_REQUEST,
                        "Not all chunks have been uploaded"
                );
            }
        }
        uploadSessionMapper.updateStatus(session.getId(), UploadSessionStatus.COMPLETING, now);
        session.setStatus(UploadSessionStatus.COMPLETING);
        return session;
    }

    private void assembleAndValidate(UploadSession session) {
        String finalKey = UploadObjectKeys.raw(session.getFileSha256());
        MessageDigest digest = sha256Digest();
        Enumeration<InputStream> chunks = new Enumeration<>() {
            private int index = 0;

            @Override
            public boolean hasMoreElements() {
                return index < session.getTotalChunks();
            }

            @Override
            public InputStream nextElement() {
                int current = index++;
                long size = UploadMath.expectedChunkSize(
                        session.getFileSizeBytes(),
                        session.getChunkSizeBytes(),
                        current
                );
                try {
                    return videoStorage.open(UploadObjectKeys.chunk(session.getId(), current), 0, size).stream();
                } catch (VideoStorageException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };

        try (SequenceInputStream sequential = new SequenceInputStream(chunks);
             DigestInputStream digestStream = new DigestInputStream(sequential, digest)) {
            videoStorage.put(finalKey, digestStream, session.getFileSizeBytes(), session.getContentType());
        } catch (VideoStorageException | IllegalStateException | IOException ex) {
            safeDelete(finalKey);
            markFailed(session);
            if (ex instanceof VideoStorageException || ex instanceof IllegalStateException) {
                throw storageError();
            }
            throw storageError();
        }

        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!actualHash.equals(session.getFileSha256())) {
            safeDelete(finalKey);
            markFailed(session);
            throw new ApiException(
                    ErrorCode.UPLOAD_HASH_MISMATCH,
                    HttpStatus.BAD_REQUEST,
                    "Assembled file hash does not match the declared SHA-256"
            );
        }
        try {
            if (videoStorage.size(finalKey) != session.getFileSizeBytes()) {
                safeDelete(finalKey);
                markFailed(session);
                throw new ApiException(
                        ErrorCode.UPLOAD_HASH_MISMATCH,
                        HttpStatus.BAD_REQUEST,
                        "Assembled file size does not match the declared size"
                );
            }
        } catch (VideoStorageException ex) {
            safeDelete(finalKey);
            markFailed(session);
            throw storageError();
        }
    }

    private MediaObject persistMedia(UploadSession session) {
        MediaObject existing = mediaObjectMapper.findBySha256(session.getFileSha256());
        if (existing != null) {
            return existing;
        }
        MediaObject media = new MediaObject();
        media.setSha256(session.getFileSha256());
        media.setObjectKey(UploadObjectKeys.raw(session.getFileSha256()));
        media.setFileSizeBytes(session.getFileSizeBytes());
        media.setContentType(session.getContentType());
        Instant now = Instant.now();
        media.setProcessingStatus(MediaProcessingStatus.PENDING);
        media.setProcessingAttempts(0);
        media.setCreatedAt(now);
        media.setUpdatedAt(now);
        try {
            mediaObjectMapper.insert(media);
            return media;
        } catch (DataIntegrityViolationException ex) {
            MediaObject winner = mediaObjectMapper.findBySha256(session.getFileSha256());
            if (winner == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save media metadata");
            }
            return winner;
        }
    }

    private Video insertVideo(UploadSession session, MediaObject media, String title, String description) {
        Instant now = Instant.now();
        Video video = new Video();
        video.setOwnerUserId(session.getUserId());
        video.setTitle(title);
        video.setDescription(description);
        video.setObjectKey(media.getObjectKey());
        video.setMediaObjectId(media.getId());
        video.setFileSha256(media.getSha256());
        video.setOriginalFilename(session.getFileName());
        video.setContentType(session.getContentType());
        video.setFileSizeBytes(session.getFileSizeBytes());
        video.setStatus(VideoStatus.UPLOADED);
        video.setCreatedAt(now);
        video.setUpdatedAt(now);
        videoMapper.insert(video);
        return video;
    }

    private CompleteUploadResponse completedResponse(UploadSession session, boolean deduplicated) {
        Video video = videoMapper.findById(session.getFinalVideoId());
        if (video == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Completed upload is missing its video");
        }
        if (video.getMediaObjectId() != null) {
            MediaObject media = mediaObjectMapper.findById(video.getMediaObjectId());
            if (media != null) {
                video.setProcessingStatus(media.getProcessingStatus());
            }
        }
        User owner = requireUser(session.getUserId());
        return new CompleteUploadResponse(VideoResponse.from(video, owner), deduplicated);
    }

    private InitUploadResponse toInitResponse(UploadSession session, MediaObject media) {
        List<Integer> uploaded = uploadChunkMapper.findIndexes(session.getId());
        boolean uploadRequired = media == null && !session.isDeduplicated();
        return new InitUploadResponse(
                session.getId(),
                session.getChunkSizeBytes(),
                session.getTotalChunks(),
                uploaded,
                media != null || session.isDeduplicated(),
                uploadRequired,
                media == null ? null : media.getId(),
                session.getStatus().name(),
                session.getExpiresAt()
        );
    }

    private UploadSession requireOwnedSession(Long userId, UUID uploadId) {
        UploadSession session = uploadSessionMapper.findById(uploadId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw notFound();
        }
        Instant now = Instant.now();
        if (session.getStatus() != UploadSessionStatus.COMPLETED && isExpired(session, now)) {
            expire(session);
            throw expired();
        }
        if (session.getStatus() == UploadSessionStatus.EXPIRED) {
            throw expired();
        }
        return session;
    }

    private void expire(UploadSession session) {
        uploadSessionMapper.updateStatus(session.getId(), UploadSessionStatus.EXPIRED, Instant.now());
        session.setStatus(UploadSessionStatus.EXPIRED);
        deleteTemporaryChunks(session.getId());
    }

    private void markFailed(UploadSession session) {
        uploadSessionMapper.updateStatus(session.getId(), UploadSessionStatus.FAILED, Instant.now());
        session.setStatus(UploadSessionStatus.FAILED);
    }

    private void deleteTemporaryChunks(UUID uploadId) {
        try {
            videoStorage.deletePrefix(UploadObjectKeys.chunkPrefix(uploadId));
        } catch (RuntimeException ex) {
            log.warn("Failed to delete temporary chunks for upload {}", uploadId, ex);
        }
        try {
            uploadChunkMapper.deleteBySessionId(uploadId);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete chunk rows for upload {}", uploadId, ex);
        }
    }

    private void safeDelete(String objectKey) {
        try {
            videoStorage.delete(objectKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete object {}", objectKey, ex);
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }

    private long validateFileSize(Long fileSizeBytes) {
        if (fileSizeBytes == null || fileSizeBytes <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "File size must be greater than 0");
        }
        if (fileSizeBytes > videoProperties.maxFileSizeBytes()) {
            throw new ApiException(
                    ErrorCode.UPLOAD_FILE_TOO_LARGE,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Video file exceeds the maximum upload size"
            );
        }
        return fileSizeBytes;
    }

    private static String validateSha256(String fileSha256) {
        if (!UploadMath.isSha256(fileSha256)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "fileSha256 must be a 64-character hex digest");
        }
        return UploadMath.normalizeSha256(fileSha256);
    }

    private static boolean isExpired(UploadSession session, Instant now) {
        return session.getExpiresAt() != null && session.getExpiresAt().isBefore(now);
    }

    private static boolean allChunksPresent(List<Integer> uploaded, int totalChunks) {
        if (uploaded.size() != totalChunks) {
            return false;
        }
        Set<Integer> present = new HashSet<>(uploaded);
        for (int i = 0; i < totalChunks; i++) {
            if (!present.contains(i)) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> missingChunks(int totalChunks, List<Integer> uploaded) {
        Set<Integer> present = new HashSet<>(uploaded);
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!present.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    private static void drain(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ignored) {
            // Best-effort consume of an idempotent retry body.
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.UPLOAD_NOT_FOUND, HttpStatus.NOT_FOUND, "Upload was not found");
    }

    private static ApiException expired() {
        return new ApiException(ErrorCode.UPLOAD_EXPIRED, HttpStatus.GONE, "Upload session has expired");
    }

    private static ApiException storageError() {
        return new ApiException(
                ErrorCode.UPLOAD_STORAGE_ERROR,
                HttpStatus.BAD_GATEWAY,
                "Video storage is unavailable"
        );
    }
}
