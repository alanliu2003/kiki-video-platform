package com.kiki.video.api.video.mapper;

import com.kiki.video.api.video.model.Video;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoMapper {

    @Insert("""
            INSERT INTO videos (
                owner_user_id, title, description, object_key, media_object_id, file_sha256,
                original_filename, content_type, file_size_bytes, status, created_at, updated_at
            ) VALUES (
                #{ownerUserId}, #{title}, #{description}, #{objectKey}, #{mediaObjectId}, #{fileSha256},
                #{originalFilename}, #{contentType}, #{fileSizeBytes}, #{status}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Video video);

    @Select("""
            SELECT v.id, v.owner_user_id, v.title, v.description, v.object_key, v.media_object_id, v.file_sha256,
                   v.original_filename, v.content_type, v.file_size_bytes, v.status, v.created_at, v.updated_at,
                   m.processing_status
            FROM videos v
            LEFT JOIN media_objects m ON m.id = v.media_object_id
            WHERE v.id = #{id}
            """)
    Video findById(Long id);

    @Select("""
            SELECT v.id, v.owner_user_id, v.title, v.description, v.object_key, v.media_object_id, v.file_sha256,
                   v.original_filename, v.content_type, v.file_size_bytes, v.status, v.created_at, v.updated_at,
                   m.processing_status
            FROM videos v
            LEFT JOIN media_objects m ON m.id = v.media_object_id
            WHERE v.owner_user_id = #{ownerUserId}
            ORDER BY v.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Video> findByOwnerUserId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT COUNT(*)
            FROM videos
            WHERE owner_user_id = #{ownerUserId}
            """)
    long countByOwnerUserId(Long ownerUserId);
}
