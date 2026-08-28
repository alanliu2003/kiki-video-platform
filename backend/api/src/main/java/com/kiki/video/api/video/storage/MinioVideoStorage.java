package com.kiki.video.api.video.storage;

import com.kiki.video.api.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioVideoStorage implements VideoStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioVideoStorage.class);
    private static final long UNKNOWN_PART_SIZE = -1;

    private final MinioClient minioClient;
    private final String bucket;

    public MinioVideoStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.videoBucket();
    }

    @Override
    public void ensureReady() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO video bucket {}", bucket);
            }
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
