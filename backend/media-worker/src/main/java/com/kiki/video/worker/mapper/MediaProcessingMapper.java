package com.kiki.video.worker.mapper;

import com.kiki.video.worker.model.ProcessingMediaObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface MediaProcessingMapper {

    @Select("""
            SELECT id, sha256, object_key, file_size_bytes, processing_status, processing_attempts,
                   processing_error, processed_prefix, master_playlist_key, thumbnail_key,
                   duration_seconds, source_width, source_height, updated_at
            FROM media_objects
            WHERE id = #{id}
            """)
    ProcessingMediaObject findById(Long id);

    @Update("""
            UPDATE media_objects
            SET processing_status = 'PROCESSING',
                processing_attempts = processing_attempts + 1,
                processing_error = NULL,
                updated_at = #{now}
            WHERE id = #{id}
              AND processing_attempts < #{maxAttempts}
              AND (
                    processing_status IN ('PENDING', 'FAILED')
                    OR (processing_status = 'PROCESSING' AND updated_at <= #{staleBefore})
                  )
            """)
    int claim(
            @Param("id") Long id,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") Instant staleBefore,
            @Param("now") Instant now
    );

    @Update("""
            UPDATE media_objects
            SET processing_status = 'READY',
                processed_prefix = #{processedPrefix},
                master_playlist_key = #{masterPlaylistKey},
                thumbnail_key = #{thumbnailKey},
                duration_seconds = #{durationSeconds},
                source_width = #{sourceWidth},
                source_height = #{sourceHeight},
                processing_error = NULL,
                processed_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
              AND processing_status = 'PROCESSING'
            """)
    int markReady(
            @Param("id") Long id,
            @Param("processedPrefix") String processedPrefix,
            @Param("masterPlaylistKey") String masterPlaylistKey,
            @Param("thumbnailKey") String thumbnailKey,
            @Param("durationSeconds") Double durationSeconds,
            @Param("sourceWidth") Integer sourceWidth,
            @Param("sourceHeight") Integer sourceHeight,
            @Param("now") Instant now
    );

    @Update("""
            UPDATE media_objects
            SET processing_status = 'FAILED',
                processing_error = #{processingError},
                updated_at = #{now}
            WHERE id = #{id}
              AND processing_status = 'PROCESSING'
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("processingError") String processingError,
            @Param("now") Instant now
    );

    @Insert("""
            INSERT INTO media_processing_outbox (
                media_object_id, event_type, event_version, payload, status, attempt_count,
                next_attempt_at, created_at, updated_at
            ) VALUES (
                #{mediaObjectId}, #{eventType}, #{eventVersion}, #{payload}, 'PENDING',
                0, #{nextAttemptAt}, #{now}, #{now}
            )
            """)
    int insertRetryOutbox(
            @Param("mediaObjectId") Long mediaObjectId,
            @Param("eventType") String eventType,
            @Param("eventVersion") int eventVersion,
            @Param("payload") String payload,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("now") Instant now
    );
}
