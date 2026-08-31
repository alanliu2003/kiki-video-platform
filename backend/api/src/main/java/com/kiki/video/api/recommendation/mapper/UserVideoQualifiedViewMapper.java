package com.kiki.video.api.recommendation.mapper;

import com.kiki.video.api.recommendation.model.QualifiedViewRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserVideoQualifiedViewMapper {

    @Insert("""
            INSERT INTO user_video_qualified_views (user_id, video_id, qualified_view_count, last_qualified_at)
            VALUES (#{userId}, #{videoId}, 1, NOW())
            ON CONFLICT (user_id, video_id) DO UPDATE
            SET qualified_view_count = user_video_qualified_views.qualified_view_count + 1,
                last_qualified_at = NOW()
            """)
    int upsertIncrement(@Param("userId") long userId, @Param("videoId") long videoId);

    @Select("""
            SELECT video_id, qualified_view_count
            FROM user_video_qualified_views
            WHERE user_id = #{userId}
            ORDER BY last_qualified_at DESC
            LIMIT #{limit}
            """)
    List<QualifiedViewRow> findRecentByUser(@Param("userId") long userId, @Param("limit") int limit);
}
