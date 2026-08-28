package com.kiki.video.api.upload.mapper;

import com.kiki.video.api.upload.model.UploadChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UploadChunkMapper {

    @Insert("""
            INSERT INTO upload_chunks (
                upload_session_id, chunk_index, chunk_size_bytes, chunk_sha256, created_at
            ) VALUES (
                #{uploadSessionId}, #{chunkIndex}, #{chunkSizeBytes}, #{chunkSha256}, #{createdAt}
            )
            """)
    int insert(UploadChunk chunk);

    @Select("""
            SELECT upload_session_id, chunk_index, chunk_size_bytes, chunk_sha256, created_at
            FROM upload_chunks
            WHERE upload_session_id = #{uploadSessionId}
              AND chunk_index = #{chunkIndex}
            """)
    UploadChunk find(
            @Param("uploadSessionId") UUID uploadSessionId,
            @Param("chunkIndex") int chunkIndex
    );

    @Select("""
            SELECT chunk_index
            FROM upload_chunks
            WHERE upload_session_id = #{uploadSessionId}
            ORDER BY chunk_index
            """)
    List<Integer> findIndexes(@Param("uploadSessionId") UUID uploadSessionId);

    @Select("""
            SELECT COUNT(*)
            FROM upload_chunks
            WHERE upload_session_id = #{uploadSessionId}
            """)
    int countBySessionId(@Param("uploadSessionId") UUID uploadSessionId);

    @Delete("""
            DELETE FROM upload_chunks
            WHERE upload_session_id = #{uploadSessionId}
            """)
    int deleteBySessionId(@Param("uploadSessionId") UUID uploadSessionId);
}
