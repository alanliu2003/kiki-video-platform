package com.kiki.video.api.upload.mapper;

import com.kiki.video.api.upload.model.UploadSession;
import com.kiki.video.api.upload.model.UploadSessionStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UploadSessionMapper {

    @Insert("""
            INSERT INTO upload_sessions (
                id, user_id, file_name, file_size_bytes, file_sha256, content_type,
                chunk_size_bytes, total_chunks, status, deduplicated, final_video_id,
                created_at, updated_at, expires_at
            ) VALUES (
                #{id}, #{userId}, #{fileName}, #{fileSizeBytes}, #{fileSha256}, #{contentType},
                #{chunkSizeBytes}, #{totalChunks}, #{status}, #{deduplicated}, #{finalVideoId},
                #{createdAt}, #{updatedAt}, #{expiresAt}
            )
            """)
    int insert(UploadSession session);

    @Select("""
            SELECT id, user_id, file_name, file_size_bytes, file_sha256, content_type,
                   chunk_size_bytes, total_chunks, status, deduplicated, final_video_id,
                   created_at, updated_at, expires_at
            FROM upload_sessions
            WHERE id = #{id}
            """)
    UploadSession findById(UUID id);

    @Select("""
            SELECT id, user_id, file_name, file_size_bytes, file_sha256, content_type,
                   chunk_size_bytes, total_chunks, status, deduplicated, final_video_id,
                   created_at, updated_at, expires_at
            FROM upload_sessions
            WHERE id = #{id}
            FOR UPDATE
            """)
    UploadSession findByIdForUpdate(UUID id);

    @Select("""
            SELECT id, user_id, file_name, file_size_bytes, file_sha256, content_type,
                   chunk_size_bytes, total_chunks, status, deduplicated, final_video_id,
                   created_at, updated_at, expires_at
            FROM upload_sessions
            WHERE user_id = #{userId}
              AND file_sha256 = #{fileSha256}
              AND file_size_bytes = #{fileSizeBytes}
              AND status IN ('INITIATED', 'UPLOADING', 'COMPLETING')
            ORDER BY created_at DESC
            LIMIT 1
            """)
    UploadSession findActiveByUserHashAndSize(
            @Param("userId") Long userId,
            @Param("fileSha256") String fileSha256,
            @Param("fileSizeBytes") long fileSizeBytes
    );

    @Select("""
            SELECT id, user_id, file_name, file_size_bytes, file_sha256, content_type,
                   chunk_size_bytes, total_chunks, status, deduplicated, final_video_id,
                   created_at, updated_at, expires_at
            FROM upload_sessions
            WHERE expires_at < #{now}
              AND status IN ('INITIATED', 'UPLOADING', 'COMPLETING', 'FAILED')
            """)
    List<UploadSession> findExpired(@Param("now") Instant now);

    @Update("""
            UPDATE upload_sessions
            SET status = #{status},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateStatus(
            @Param("id") UUID id,
            @Param("status") UploadSessionStatus status,
            @Param("updatedAt") Instant updatedAt
    );

    @Update("""
            UPDATE upload_sessions
            SET status = 'COMPLETED',
                final_video_id = #{videoId},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int markCompleted(
            @Param("id") UUID id,
            @Param("videoId") Long videoId,
            @Param("updatedAt") Instant updatedAt
    );

    @Update("""
            UPDATE upload_sessions
            SET deduplicated = #{deduplicated},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateDeduplicated(
            @Param("id") UUID id,
            @Param("deduplicated") boolean deduplicated,
            @Param("updatedAt") Instant updatedAt
    );

    @Update("""
            UPDATE upload_sessions
            SET expires_at = #{expiresAt},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateExpiresAt(
            @Param("id") UUID id,
            @Param("expiresAt") Instant expiresAt,
            @Param("updatedAt") Instant updatedAt
    );
}
