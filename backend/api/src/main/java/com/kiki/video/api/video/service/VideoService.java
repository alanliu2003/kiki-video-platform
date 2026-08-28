package com.kiki.video.api.video.service;

import com.kiki.video.api.config.VideoProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.media.MediaProcessingRequestService;
import com.kiki.video.api.upload.UploadObjectKeys;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.VideoValidators;
import com.kiki.video.api.video.dto.VideoListResponse;
import com.kiki.video.api.video.dto.VideoResponse;
import com.kiki.video.api.video.dto.VideoSummaryResponse;
import com.kiki.video.api.video.dto.VideoUploadResponse;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.api.video.model.VideoStatus;
import com.kiki.video.api.video.storage.StoredVideoObject;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final MediaObjectMapper mediaObjectMapper;
    private final VideoStorage videoStorage;
    private final VideoProperties videoProperties;
    private final MediaProcessingRequestService mediaProcessingRequestService;
    private final TransactionTemplate transactionTemplate;

    public VideoService(
            VideoMapper videoMapper,
            UserMapper userMapper,
            MediaObjectMapper mediaObjectMapper,
            VideoStorage videoStorage,
            VideoProperties videoProperties,
            MediaProcessingRequestService mediaProcessingRequestService,
            PlatformTransactionManager transactionManager
    ) {
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.mediaObjectMapper = mediaObjectMapper;
        this.videoStorage = videoStorage;
        this.videoProperties = videoProperties;
        this.mediaProcessingRequestService = mediaProcessingRequestService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public VideoUploadResponse upload(Long ownerUserId, String title, String description, MultipartFile file) {
        User owner = requireUser(ownerUserId);
        String normalizedTitle = VideoValidators.validateTitle(title);
        String normalizedDescription = VideoValidators.validateDescription(description);
        MultipartFile videoFile = requireFile(file);
        String contentType = VideoValidators.validateContentType(videoFile.getContentType());
        long size = videoFile.getSize();
        validateSize(size);

        String originalFilename = VideoValidators.safeOriginalFilename(videoFile.getOriginalFilename());
        String tempKey = "uploads/legacy/" + UUID.randomUUID();
        MessageDigest digest = sha256Digest();

        try (InputStream content = videoFile.getInputStream();
             DigestInputStream digestStream = new DigestInputStream(content, digest)) {
            videoStorage.put(tempKey, digestStream, size, contentType);
        } catch (VideoStorageException | IOException ex) {
            throw storageError();
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        MediaObject media;
        try {
            media = persistLegacyMedia(tempKey, sha256, size, contentType);
        } catch (RuntimeException ex) {
            compensateUpload(tempKey);
            throw ex instanceof ApiException api ? api : storageError();
        }

        Instant now = Instant.now();
        Video video = new Video();
        video.setOwnerUserId(ownerUserId);
        video.setTitle(normalizedTitle);
        video.setDescription(normalizedDescription);
        video.setObjectKey(media.getObjectKey());
        video.setMediaObjectId(media.getId());
        video.setFileSha256(media.getSha256());
        video.setOriginalFilename(originalFilename);
        video.setContentType(contentType);
        video.setFileSizeBytes(size);
        video.setStatus(VideoStatus.UPLOADED);
        video.setCreatedAt(now);
        video.setUpdatedAt(now);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                videoMapper.insert(video);
                mediaProcessingRequestService.requestIfNeeded(media);
            });
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save video metadata");
        }

        MediaObject currentMedia = mediaObjectMapper.findById(media.getId());
        if (currentMedia != null) {
            video.setProcessingStatus(currentMedia.getProcessingStatus());
        }
        return VideoUploadResponse.from(video, owner);
    }

    public VideoResponse getVideo(Long videoId) {
        Video video = requireVideo(videoId);
        User owner = requireUser(video.getOwnerUserId());
        return VideoResponse.from(video, owner);
    }

    public VideoListResponse listMine(Long ownerUserId, Integer page, Integer size) {
        requireUser(ownerUserId);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int offset = safePage * safeSize;
        List<VideoSummaryResponse> items = videoMapper.findByOwnerUserId(ownerUserId, safeSize, offset)
                .stream()
                .map(VideoSummaryResponse::from)
                .toList();
        long total = videoMapper.countByOwnerUserId(ownerUserId);
        return new VideoListResponse(items, safePage, safeSize, total);
    }

    public Video requireVideo(Long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Video was not found");
        }
        return video;
    }

    public MediaObject requireMedia(Video video) {
        if (video.getMediaObjectId() == null) {
            return null;
        }
        return mediaObjectMapper.findById(video.getMediaObjectId());
    }

    public StoredVideoObject openContent(Video video, long offset, long length) {
        try {
            return videoStorage.open(video.getObjectKey(), offset, length);
        } catch (VideoStorageException ex) {
            throw storageError();
        }
    }

    public StoredVideoObject openObject(String objectKey, long offset, long length) {
        try {
            return videoStorage.open(objectKey, offset, length);
        } catch (VideoStorageException ex) {
            throw storageError();
        }
    }

    public long objectSize(String objectKey) {
        try {
            return videoStorage.size(objectKey);
        } catch (VideoStorageException ex) {
            throw storageError();
        }
    }

    public boolean objectExists(String objectKey) {
        try {
            return videoStorage.exists(objectKey);
        } catch (VideoStorageException ex) {
            throw storageError();
        }
    }

    public long contentSize(Video video) {
        try {
            return videoStorage.size(video.getObjectKey());
        } catch (VideoStorageException ex) {
            throw storageError();
        }
    }

    private MediaObject persistLegacyMedia(String tempKey, String sha256, long size, String contentType) {
        MediaObject existing = mediaObjectMapper.findBySha256(sha256);
        if (existing != null) {
            compensateUpload(tempKey);
            return existing;
        }
        String rawKey = UploadObjectKeys.raw(sha256);
        try {
            videoStorage.copy(tempKey, rawKey);
        } catch (VideoStorageException ex) {
            compensateUpload(tempKey);
            throw storageError();
        }
        compensateUpload(tempKey);

        Instant now = Instant.now();
        MediaObject media = new MediaObject();
        media.setSha256(sha256);
        media.setObjectKey(rawKey);
        media.setFileSizeBytes(size);
        media.setContentType(contentType);
        media.setProcessingStatus(MediaProcessingStatus.PENDING);
        media.setProcessingAttempts(0);
        media.setCreatedAt(now);
        media.setUpdatedAt(now);
        try {
            mediaObjectMapper.insert(media);
            return media;
        } catch (DataIntegrityViolationException ex) {
            MediaObject winner = mediaObjectMapper.findBySha256(sha256);
            if (winner == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save media metadata");
            }
            return winner;
        }
    }

    private void compensateUpload(String objectKey) {
        try {
            videoStorage.delete(objectKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete MinIO object {} after a metadata failure", objectKey, ex);
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }

    private static MultipartFile requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VIDEO_FILE_REQUIRED, HttpStatus.BAD_REQUEST, "A video file is required");
        }
        return file;
    }

    private void validateSize(long size) {
        if (size > videoProperties.maxUploadSizeBytes()) {
            throw new ApiException(
                    ErrorCode.VIDEO_FILE_TOO_LARGE,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Video file exceeds the maximum upload size"
            );
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static ApiException storageError() {
        return new ApiException(
                ErrorCode.VIDEO_STORAGE_ERROR,
                HttpStatus.BAD_GATEWAY,
                "Video storage is unavailable"
        );
    }
}
