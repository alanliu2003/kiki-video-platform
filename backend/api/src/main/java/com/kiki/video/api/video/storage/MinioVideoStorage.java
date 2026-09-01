package com.kiki.video.api.video.storage;

import com.kiki.video.api.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class MinioVideoStorage implements VideoStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioVideoStorage.class);
    private static final long UNKNOWN_PART_SIZE = -1;

    private final MinioClient minioClient;
    private final String bucket;
    private final MinioBucketCorsConfigurer corsConfigurer;

    public MinioVideoStorage(
            MinioClient minioClient,
            MinioProperties properties,
            MinioBucketCorsConfigurer corsConfigurer
    ) {
        this.minioClient = minioClient;
        this.bucket = properties.videoBucket();
        this.corsConfigurer = corsConfigurer;
    }

    @Override
    public void ensureReady() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO video bucket {}", bucket);
            }
            corsConfigurer.apply();
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to reach MinIO or prepare the video bucket", ex);
        }
    }

    @Override
    public void put(String objectKey, InputStream content, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, UNKNOWN_PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to store the video object", ex);
        }
    }

    @Override
    public void putFile(String objectKey, Path file, String contentType) {
        try (InputStream content = Files.newInputStream(file)) {
            put(objectKey, content, Files.size(file), contentType);
        } catch (VideoStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to store the video object", ex);
        }
    }

    @Override
    public void downloadTo(String objectKey, Path destination) {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
             OutputStream output = Files.newOutputStream(destination)) {
            stream.transferTo(output);
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to download the video object", ex);
        }
    }

    @Override
    public void copy(String sourceKey, String destKey) {
        long objectSize = size(sourceKey);
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(sourceKey)
                .build())) {
            put(destKey, stream, objectSize, "application/octet-stream");
        } catch (VideoStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to copy the video object", ex);
        }
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                keys.add(result.get().objectName());
            }
            return keys;
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to list video objects", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to delete the video object", ex);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || prefix.equals("/") || !prefix.contains("/")) {
            throw new VideoStorageException("Refusing to delete an unsafe object prefix");
        }
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(item.objectName())
                        .build());
            }
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to delete temporary upload objects", ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException ex) {
            String code = ex.errorResponse() == null ? "" : ex.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code)) {
                return false;
            }
            throw new VideoStorageException("Unable to read video object metadata", ex);
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to read video object metadata", ex);
        }
    }

    @Override
    public long size(String objectKey) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()).size();
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to read video object metadata", ex);
        }
    }

    @Override
    public StoredVideoObject open(String objectKey, long offset, long length) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .offset(offset)
                    .length(length)
                    .build());
            return new StoredVideoObject(stream, length);
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to read the video object", ex);
        }
    }
}
