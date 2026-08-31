package com.kiki.video.api.recommendation.mapper;

import com.kiki.video.api.recommendation.model.CreatorAffinityRow;
import com.kiki.video.api.recommendation.model.RecommendationCandidateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecommendationMapper {

    @Select("""
            SELECT followed_user_id
            FROM user_follows
            WHERE follower_user_id = #{userId}
            """)
    List<Long> findFollowedCreatorIds(@Param("userId") long userId);

    @Select("""
            SELECT creator_id,
                   SUM(like_interactions)::bigint AS like_interactions,
                   SUM(favorite_interactions)::bigint AS favorite_interactions,
                   SUM(comment_interactions)::bigint AS comment_interactions,
                   SUM(view_interactions)::bigint AS view_interactions
            FROM (
                SELECT v.owner_user_id AS creator_id,
                       1 AS like_interactions,
                       0 AS favorite_interactions,
                       0 AS comment_interactions,
                       0 AS view_interactions
                FROM (
                    SELECT video_id
                    FROM video_likes
                    WHERE user_id = #{userId}
                    ORDER BY created_at DESC
                    LIMIT #{historyLimit}
                ) l
                JOIN videos v ON v.id = l.video_id
                WHERE v.owner_user_id <> #{userId}
                UNION ALL
                SELECT v.owner_user_id,
                       0, 1, 0, 0
                FROM (
                    SELECT video_id
                    FROM video_favorites
                    WHERE user_id = #{userId}
                    ORDER BY created_at DESC
                    LIMIT #{historyLimit}
                ) f
                JOIN videos v ON v.id = f.video_id
                WHERE v.owner_user_id <> #{userId}
                UNION ALL
                SELECT v.owner_user_id,
                       0, 0, 1, 0
                FROM (
                    SELECT video_id
                    FROM comments
                    WHERE author_user_id = #{userId} AND status = 'ACTIVE'
                    ORDER BY created_at DESC
                    LIMIT #{historyLimit}
                ) c
                JOIN videos v ON v.id = c.video_id
                WHERE v.owner_user_id <> #{userId}
                UNION ALL
                SELECT v.owner_user_id,
                       0, 0, 0, q.qualified_view_count
                FROM (
                    SELECT video_id, qualified_view_count
                    FROM user_video_qualified_views
                    WHERE user_id = #{userId}
                    ORDER BY last_qualified_at DESC
                    LIMIT #{historyLimit}
                ) q
                JOIN videos v ON v.id = q.video_id
                WHERE v.owner_user_id <> #{userId}
            ) signals
            GROUP BY creator_id
            ORDER BY SUM(
                like_interactions * 2
                + favorite_interactions * 3
                + comment_interactions * 2
                + view_interactions
            ) DESC, creator_id DESC
            LIMIT #{affinityCreatorLimit}
            """)
    List<CreatorAffinityRow> findCreatorAffinities(
            @Param("userId") long userId,
            @Param("historyLimit") int historyLimit,
            @Param("affinityCreatorLimit") int affinityCreatorLimit
    );

    @Select("""
            <script>
            SELECT v.id
            FROM videos v
            WHERE v.owner_user_id IN
            <foreach item="ownerId" collection="ownerIds" open="(" separator="," close=")">
                #{ownerId}
            </foreach>
              AND v.owner_user_id &lt;&gt; #{excludeOwnerId}
            ORDER BY v.created_at DESC, v.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Long> findRecentVideoIdsByOwners(
            @Param("ownerIds") List<Long> ownerIds,
            @Param("excludeOwnerId") long excludeOwnerId,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT v.id,
                   v.title,
                   u.id AS owner_id,
                   u.username AS owner_username,
                   u.display_name AS owner_display_name,
                   v.created_at,
                   m.duration_seconds,
                   (m.thumbnail_key IS NOT NULL) AS thumbnail_available,
                   m.processing_status,
                   v.view_count,
                   COALESCE(l.like_count, 0) AS like_count,
                   COALESCE(f.favorite_count, 0) AS favorite_count,
                   COALESCE(c.comment_count, 0) AS comment_count
            FROM videos v
            JOIN users u ON u.id = v.owner_user_id
            LEFT JOIN media_objects m ON m.id = v.media_object_id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS like_count
                FROM video_likes
                WHERE video_id IN
                <foreach item="id" collection="ids" open="(" separator="," close=")">
                    #{id}
                </foreach>
                GROUP BY video_id
            ) l ON l.video_id = v.id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS favorite_count
                FROM video_favorites
                WHERE video_id IN
                <foreach item="id" collection="ids" open="(" separator="," close=")">
                    #{id}
                </foreach>
                GROUP BY video_id
            ) f ON f.video_id = v.id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS comment_count
                FROM comments
                WHERE status = 'ACTIVE'
                  AND video_id IN
                <foreach item="id" collection="ids" open="(" separator="," close=")">
                    #{id}
                </foreach>
                GROUP BY video_id
            ) c ON c.video_id = v.id
            WHERE v.id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<RecommendationCandidateRow> findCandidatesByIds(@Param("ids") List<Long> ids);
}
