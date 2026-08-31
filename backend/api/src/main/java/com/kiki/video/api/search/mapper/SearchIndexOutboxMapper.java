package com.kiki.video.api.search.mapper;

import com.kiki.video.api.search.model.SearchIndexOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface SearchIndexOutboxMapper {

    @Insert("""
            INSERT INTO search_index_outbox (
                video_id, event_type, event_version, payload, status, attempt_count,
                next_attempt_at, created_at, updated_at
            ) VALUES (
                #{videoId}, #{eventType}, #{eventVersion}, #{payload}, #{status},
                #{attemptCount}, #{nextAttemptAt}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(SearchIndexOutbox row);

    @Select("""
            SELECT id, video_id, event_type, event_version, payload, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, published_at
            FROM search_index_outbox
            WHERE id = #{id}
            """)
    SearchIndexOutbox findById(Long id);

    @Select("""
            SELECT id, video_id, event_type, event_version, payload, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, published_at
            FROM search_index_outbox
            WHERE video_id = #{videoId}
              AND event_type = 'VIDEO_SEARCH_UPSERT'
              AND status IN ('PENDING', 'PUBLISHING')
            """)
    SearchIndexOutbox findActiveUpsert(Long videoId);

    @Select("""
            SELECT id, video_id, event_type, event_version, payload, status, attempt_count,
                   next_attempt_at, last_error, created_at, updated_at, published_at
            FROM search_index_outbox
            WHERE video_id = #{videoId}
            ORDER BY id DESC
            LIMIT 1
            """)
    SearchIndexOutbox findLatestByVideoId(Long videoId);

    @Select("""
            WITH due AS (
                SELECT id
                FROM search_index_outbox
                WHERE (status = 'PENDING' AND next_attempt_at <= #{now})
                   OR (status = 'PUBLISHING' AND updated_at <= #{staleBefore})
                ORDER BY id
                LIMIT #{limit}
                FOR UPDATE SKIP LOCKED
            )
            UPDATE search_index_outbox o
            SET status = 'PUBLISHING',
                attempt_count = o.attempt_count + 1,
                updated_at = #{now}
            FROM due
            WHERE o.id = due.id
            RETURNING o.id, o.video_id, o.event_type, o.event_version, o.payload, o.status,
                      o.attempt_count, o.next_attempt_at, o.last_error, o.created_at, o.updated_at,
                      o.published_at
            """)
    List<SearchIndexOutbox> claimDue(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE search_index_outbox
            SET status = 'PUBLISHED',
                published_at = #{publishedAt},
                last_error = NULL,
                updated_at = #{publishedAt}
            WHERE id = #{id}
            """)
    int markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);

    @Update("""
            UPDATE search_index_outbox
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
