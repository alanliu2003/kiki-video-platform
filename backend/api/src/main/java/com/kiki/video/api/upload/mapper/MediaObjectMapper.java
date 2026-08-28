package com.kiki.video.api.upload.mapper;

import com.kiki.video.api.upload.model.MediaObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface MediaObjectMapper {

    String COLUMNS = """
            id, sha256, object_key, file_size_bytes, content_type, processing_status,
            processing_attempts, processing_error, processed_prefix, master_playlist_key,
            thumbnail_key, duration_seconds, source_width, source_height, created_at,
            updated_at, processed_at
            """;

    @Insert("""
            INSERT INTO media_objects (
                sha256, object_key, file_size_bytes, content_type, processing_status,
                processing_attempts, created_at, updated_at
            ) VALUES (
                #{sha256}, #{objectKey}, #{fileSizeBytes}, #{contentType}, #{processingStatus},
                #{processingAttempts}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MediaObject mediaObject);

    @Select("SELECT " + COLUMNS + " FROM media_objects WHERE sha256 = #{sha256}")
    MediaObject findBySha256(String sha256);

    @Select("SELECT " + COLUMNS + " FROM media_objects WHERE id = #{id}")
    MediaObject findById(Long id);

    @Update("""
            UPDATE media_objects
            SET processing_status = 'PENDING',
                processing_error = NULL,
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND processing_status IN ('NOT_REQUESTED', 'FAILED')
              AND processing_attempts < #{maxAttempts}
            """)
    int markPendingIfEligible(
            @Param("id") Long id,
            @Param("maxAttempts") int maxAttempts,
            @Param("updatedAt") Instant updatedAt
    );
}
