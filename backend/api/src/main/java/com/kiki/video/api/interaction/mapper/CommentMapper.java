package com.kiki.video.api.interaction.mapper;

import com.kiki.video.api.interaction.model.Comment;
import com.kiki.video.api.interaction.model.VideoInteractionCounts;
import com.kiki.video.api.interaction.model.VideoViewerState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("""
            INSERT INTO comments (
                video_id, author_user_id, parent_comment_id, content, status, created_at, updated_at
            ) VALUES (
                #{videoId}, #{authorUserId}, #{parentCommentId}, #{content}, #{status},
                #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Comment comment);

    @Select("""
            SELECT c.id, c.video_id, c.author_user_id, c.parent_comment_id, c.content, c.status,
                   c.created_at, c.updated_at, u.username AS author_username, u.display_name AS author_display_name
            FROM comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.id = #{id}
            """)
    Comment findById(Long id);

    @Select("""
            SELECT c.id, c.video_id, c.author_user_id, c.parent_comment_id, c.content, c.status,
                   c.created_at, c.updated_at, u.username AS author_username, u.display_name AS author_display_name
            FROM comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.video_id = #{videoId}
              AND c.parent_comment_id IS NULL
              AND c.status = 'ACTIVE'
            ORDER BY c.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Comment> findTopLevelByVideoId(
            @Param("videoId") Long videoId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT c.id, c.video_id, c.author_user_id, c.parent_comment_id, c.content, c.status,
                   c.created_at, c.updated_at, u.username AS author_username, u.display_name AS author_display_name
            FROM comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.parent_comment_id IN
            <foreach item="id" collection="parentIds" open="(" separator="," close=")">
                #{id}
            </foreach>
              AND c.status = 'ACTIVE'
            ORDER BY c.created_at ASC
            </script>
            """)
    List<Comment> findRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    @Select("""
            SELECT COUNT(*)
            FROM comments
            WHERE video_id = #{videoId}
              AND parent_comment_id IS NULL
              AND status = 'ACTIVE'
            """)
    long countTopLevelByVideoId(Long videoId);

    @Select("""
            SELECT COUNT(*)
            FROM comments
            WHERE video_id = #{videoId}
              AND status = 'ACTIVE'
            """)
    long countActiveByVideoId(Long videoId);

    @Select("""
            SELECT
                (SELECT COUNT(*) FROM video_likes WHERE video_id = #{videoId}) AS likeCount,
                (SELECT COUNT(*) FROM video_favorites WHERE video_id = #{videoId}) AS favoriteCount,
                (SELECT COUNT(*) FROM comments WHERE video_id = #{videoId} AND status = 'ACTIVE') AS commentCount
            """)
    VideoInteractionCounts countVideoInteractions(Long videoId);

    @Select("""
            SELECT
                EXISTS(
                    SELECT 1 FROM video_likes
                    WHERE user_id = #{userId} AND video_id = #{videoId}
                ) AS liked,
                EXISTS(
                    SELECT 1 FROM video_favorites
                    WHERE user_id = #{userId} AND video_id = #{videoId}
                ) AS favorited
            """)
    VideoViewerState findViewerState(@Param("userId") Long userId, @Param("videoId") Long videoId);
}
