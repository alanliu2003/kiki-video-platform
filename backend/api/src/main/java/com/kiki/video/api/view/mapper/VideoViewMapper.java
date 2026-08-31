package com.kiki.video.api.view.mapper;

import com.kiki.video.api.view.model.VideoViewCount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

@Mapper
public interface VideoViewMapper {

    @Update("""
            UPDATE videos
            SET view_count = view_count + 1, updated_at = NOW()
            WHERE id = #{videoId}
            """)
    int incrementViewCount(@Param("videoId") Long videoId);

    @Select("SELECT view_count FROM videos WHERE id = #{videoId}")
    Long findViewCount(@Param("videoId") Long videoId);

    @Insert("""
            INSERT INTO video_view_idempotency (video_id, client_view_id)
            VALUES (#{videoId}, #{clientViewId})
            ON CONFLICT DO NOTHING
            """)
    int insertIdempotency(@Param("videoId") Long videoId, @Param("clientViewId") UUID clientViewId);

    @Select("""
            <script>
            SELECT id AS videoId, view_count AS viewCount
            FROM videos
            WHERE id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<VideoViewCount> findViewCounts(@Param("ids") List<Long> ids);
}
