package com.kiki.video.api.video.service;

import com.kiki.video.api.config.VideoProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.VideoObjectKeys;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final VideoStorage videoStorage;
    private final VideoProperties videoProperties;

    public VideoService(
            VideoMapper videoMapper,
            UserMapper userMapper,
            VideoStorage videoStorage,
            VideoProperties videoProperties
    ) {
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.videoStorage = videoStorage;
        this.videoProperties = videoProperties;
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
        String objectKey = VideoObjectKeys.create(ownerUserId, contentType);
        Instant now = Instant.now();

        try (InputStream content = videoFile.getInputStream()) {
            videoStorage.put(objectKey, content, size, contentType);
        } catch (VideoStorageException ex) {
            throw storageError();
        } catch (IOException ex) {
            throw storageError();
        }

        Video video = new Video();
        video.setOwnerUserId(ownerUserId);
        video.setTitle(normalizedTitle);
        video.setDescription(normalizedDescription);
        video.setObjectKey(objectKey);
        video.setOriginalFilename(originalFilename);
        video.setContentType(contentType);
        video.setFileSizeBytes(size);
        video.setStatus(VideoStatus.UPLOADED);
        video.setCreatedAt(now);
        video.setUpdatedAt(now);

        try {
            videoMapper.insert(video);
        } catch (RuntimeException ex) {
            compensateUpload(objectKey);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save video metadata");
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

    public StoredVideoObject openContent(Video video, long offset, long length) {
        try {
            return videoStorage.open(video.getObjectKey(), offset, length);
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

    private void compensateUpload(String objectKey) {
        try {
            videoStorage.delete(objectKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete MinIO object {} after a database insert failure", objectKey, ex);
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

    private static ApiException storageError() {
        return new ApiException(
                ErrorCode.VIDEO_STORAGE_ERROR,
                HttpStatus.BAD_GATEWAY,
                "Video storage is unavailable"
        );
    }
}
