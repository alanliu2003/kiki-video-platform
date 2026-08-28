package com.kiki.video.worker.storage;

import com.kiki.video.worker.config.WorkerMinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(0)
public class MinioObjectStore implements ObjectStore, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStore.class);
    private static final long UNKNOWN_PART_SIZE = -1;

    private final MinioClient minioClient;
    private final String bucket;

    public MinioObjectStore(MinioClient minioClient, WorkerMinioProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.videoBucket();
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureReady();
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
            throw new IllegalStateException("Unable to reach MinIO or prepare the video bucket", ex);
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
            throw new IllegalStateException("Unable to download object " + objectKey, ex);
        }
    }

    @Override
    public void putFile(String objectKey, Path file, String contentType) {
        try (InputStream content = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, Files.size(file), UNKNOWN_PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to upload object " + objectKey, ex);
        }
    }

    @Override
    public void copy(String sourceKey, String destKey) {
        try {
            long objectSize = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(sourceKey)
                    .build()).size();
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(sourceKey)
                    .build())) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(destKey)
                        .stream(stream, objectSize, UNKNOWN_PART_SIZE)
                        .contentType("application/octet-stream")
                        .build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to copy object " + sourceKey, ex);
        }
    }

    @Override
    public List<String> list(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build())) {
                keys.add(result.get().objectName());
            }
            return keys;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to list objects for " + prefix, ex);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || prefix.equals("/") || !prefix.contains("/")) {
            throw new IllegalArgumentException("Refusing to delete an unsafe object prefix");
        }
        for (String key : list(prefix)) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build());
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to delete object " + key, ex);
            }
        }
    }
}
