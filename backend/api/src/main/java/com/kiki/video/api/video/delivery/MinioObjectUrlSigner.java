package com.kiki.video.api.video.delivery;

import com.kiki.video.api.config.MinioProperties;
import com.kiki.video.api.video.storage.VideoStorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class MinioObjectUrlSigner implements ObjectUrlSigner {

    private final MinioClient signingClient;
    private final String bucket;

    public MinioObjectUrlSigner(
            @Qualifier("minioSigningClient") MinioClient signingClient,
            MinioProperties properties
    ) {
        this.signingClient = signingClient;
        this.bucket = properties.videoBucket();
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new VideoStorageException("Refusing to sign an empty object key");
        }
        int seconds = Math.toIntExact(Math.max(1, Math.min(ttl.toSeconds(), TimeUnit.DAYS.toSeconds(7))));
        try {
            return signingClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(seconds)
                    .build());
        } catch (Exception ex) {
            throw new VideoStorageException("Unable to sign object delivery URL", ex);
        }
    }
}
