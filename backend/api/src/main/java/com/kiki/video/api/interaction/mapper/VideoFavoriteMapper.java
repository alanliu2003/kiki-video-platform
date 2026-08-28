package com.kiki.video.api.interaction.mapper;

import com.kiki.video.api.interaction.model.VideoFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VideoFavoriteMapper {

    @Insert("""
            INSERT INTO video_favorites (user_id, video_id, created_at)
            VALUES (#{userId}, #{videoId}, #{createdAt})
            ON CONFLICT (user_id, video_id) DO NOTHING
            """)
    int insertIgnore(VideoFavorite favorite);

    @Delete("""
            DELETE FROM video_favorites
            WHERE user_id = #{userId} AND video_id = #{videoId}
            """)
    int delete(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Select("""
            SELECT COUNT(*)
            FROM video_favorites
            WHERE video_id = #{videoId}
            """)
    long countByVideoId(Long videoId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM video_favorites
                WHERE user_id = #{userId} AND video_id = #{videoId}
            )
            """)
    boolean exists(@Param("userId") Long userId, @Param("videoId") Long videoId);
}
