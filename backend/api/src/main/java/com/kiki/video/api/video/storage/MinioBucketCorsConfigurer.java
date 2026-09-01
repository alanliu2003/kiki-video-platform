package com.kiki.video.api.video.storage;

import com.kiki.video.api.config.MediaDeliveryProperties;
import io.minio.MinioClient;
import io.minio.SetBucketCorsArgs;
import io.minio.messages.CORSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Community MinIO rejects S3 PutBucketCors ({@code NotImplemented}; that API is AIStor-only).
 * Local/dev therefore relies on MinIO's global {@code MINIO_API_CORS_ALLOW_ORIGIN}
 * (Compose default {@code *}, no browser credentials on object GETs).
 * This bean still attempts bucket CORS so an AIStor endpoint can pick it up, then fails soft.
 */
@Component
public class MinioBucketCorsConfigurer {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketCorsConfigurer.class);

    private final MinioClient minioClient;
    private final String videoBucket;
    private final MediaDeliveryProperties deliveryProperties;

    public MinioBucketCorsConfigurer(
            MinioClient minioClient,
            com.kiki.video.api.config.MinioProperties minioProperties,
            MediaDeliveryProperties deliveryProperties
    ) {
        this.minioClient = minioClient;
        this.videoBucket = minioProperties.videoBucket();
        this.deliveryProperties = deliveryProperties;
    }

    public void apply() {
        List<String> origins = deliveryProperties.effectiveCorsOrigins();
        CORSConfiguration.CORSRule rule = new CORSConfiguration.CORSRule(
                List.of("Range", "Content-Type", "If-Match", "If-None-Match", "If-Modified-Since"),
                List.of("GET", "HEAD"),
                origins,
                List.of("Accept-Ranges", "Content-Range", "Content-Length", "Content-Type", "ETag"),
                "kiki-browser-media",
                3600
        );
        try {
            minioClient.setBucketCors(SetBucketCorsArgs.builder()
                    .bucket(videoBucket)
                    .config(new CORSConfiguration(List.of(rule)))
                    .build());
            log.info("Applied MinIO bucket CORS for {} origin(s)", origins.size());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (message.contains("NotImplemented")) {
                log.info(
                        "MinIO does not implement bucket CORS; using global MINIO_API_CORS_ALLOW_ORIGIN. "
                                + "Bucket stays private. Run scripts/setup-minio-cors to restrict global origins."
                );
                return;
            }
            log.warn(
                    "Unable to apply MinIO bucket CORS; use MINIO_API_CORS_ALLOW_ORIGIN or scripts/setup-minio-cors: {}",
                    message
            );
        }
    }
}
