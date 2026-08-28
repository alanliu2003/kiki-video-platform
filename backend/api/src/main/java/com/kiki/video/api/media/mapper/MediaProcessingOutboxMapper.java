package com.kiki.video.api.media.mapper;

import com.kiki.video.api.media.model.MediaProcessingOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MediaProcessingOutboxMapper {

    @Insert("""
            INSERT INTO media_processing_outbox (
                media_object_id, event_type, event_version, payload, status, attempt_count,
                next_attempt_at, created_at, updated_at
            ) VALUES (
                #{mediaObjectId}, #{eventType}, #{eventVersion}, #{payload}, #{status},
                #{attemptCount}, #{nextAttemptAt}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MediaProcessingOutbox row);

    @Select("""
            SELECT id, media_object_id, event_type, event_version, payload, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, published_at
            FROM media_processing_outbox
            WHERE id = #{id}
            """)
    MediaProcessingOutbox findById(Long id);

    @Select("""
            SELECT id, media_object_id, event_type, event_version, payload, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, published_at
            FROM media_processing_outbox
            WHERE media_object_id = #{mediaObjectId}
              AND status IN ('PENDING', 'PUBLISHING')
            """)
    MediaProcessingOutbox findActiveByMediaObjectId(Long mediaObjectId);

    @Select("""
            SELECT id, media_object_id, event_type, event_version, payload, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, published_at
            FROM media_processing_outbox
            WHERE media_object_id = #{mediaObjectId}
            ORDER BY id DESC
            LIMIT 1
            """)
    MediaProcessingOutbox findLatestByMediaObjectId(Long mediaObjectId);

    @Select("""
            WITH due AS (
                SELECT id
                FROM media_processing_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= #{now})
                   OR (status = 'PUBLISHING' AND updated_at <= #{staleBefore})
                ORDER BY id
                LIMIT #{limit}
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_processing_outbox o
            SET status = 'PUBLISHING',
                attempt_count = o.attempt_count + 1,
                updated_at = #{now}
            FROM due
            WHERE o.id = due.id
            RETURNING o.id, o.media_object_id, o.event_type, o.event_version, o.payload, o.status,
                      o.attempt_count, o.next_attempt_at, o.last_error, o.created_at, o.updated_at,
                      o.published_at
            """)
    List<MediaProcessingOutbox> claimDue(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE media_processing_outbox
            SET status = 'PUBLISHED',
                published_at = #{publishedAt},
                last_error = NULL,
                updated_at = #{publishedAt}
            WHERE id = #{id}
            """)
    int markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);

    @Update("""
            UPDATE media_processing_outbox
            SET status = 'PENDING',
                next_attempt_at = #{nextAttemptAt},
                last_error = #{lastError},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int markRetry(
            @Param("id") Long id,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") Instant updatedAt
    );
}
