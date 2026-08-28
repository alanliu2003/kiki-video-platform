package com.kiki.video.api.upload.mapper;

import com.kiki.video.api.upload.model.MediaObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaObjectMapper {

    @Insert("""
            INSERT INTO media_objects (
                sha256, object_key, file_size_bytes, content_type, created_at
            ) VALUES (
                #{sha256}, #{objectKey}, #{fileSizeBytes}, #{contentType}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(MediaObject mediaObject);

    @Select("""
            SELECT id, sha256, object_key, file_size_bytes, content_type, created_at
            FROM media_objects
            WHERE sha256 = #{sha256}
            """)
    MediaObject findBySha256(String sha256);

    @Select("""
            SELECT id, sha256, object_key, file_size_bytes, content_type, created_at
            FROM media_objects
            WHERE id = #{id}
            """)
    MediaObject findById(Long id);
}
