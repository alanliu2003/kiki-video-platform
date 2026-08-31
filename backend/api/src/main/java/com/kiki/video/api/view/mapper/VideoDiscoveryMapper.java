package com.kiki.video.api.view.mapper;

import com.kiki.video.api.view.model.VideoDiscoveryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoDiscoveryMapper {

    @Select("SELECT COUNT(*) FROM videos")
    long countVideos();

    @Select("""
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
                   COALESCE(l.like_count, 0) AS like_count
            FROM videos v
            JOIN users u ON u.id = v.owner_user_id
            LEFT JOIN media_objects m ON m.id = v.media_object_id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS like_count
                FROM video_likes
                GROUP BY video_id
            ) l ON l.video_id = v.id
            ORDER BY v.created_at DESC, v.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<VideoDiscoveryRow> findRecent(@Param("limit") int limit, @Param("offset") int offset);

    @Select("""
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
                   COALESCE(l.like_count, 0) AS like_count
            FROM videos v
            JOIN users u ON u.id = v.owner_user_id
            LEFT JOIN media_objects m ON m.id = v.media_object_id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS like_count
                FROM video_likes
                GROUP BY video_id
            ) l ON l.video_id = v.id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS favorite_count
                FROM video_favorites
                GROUP BY video_id
            ) f ON f.video_id = v.id
            LEFT JOIN (
                SELECT video_id, COUNT(*)::bigint AS comment_count
                FROM comments
                WHERE status = 'ACTIVE'
                GROUP BY video_id
            ) c ON c.video_id = v.id
            ORDER BY (
                LN(1 + v.view_count) * #{viewWeight}
                + LN(1 + COALESCE(l.like_count, 0)) * #{likeWeight}
                + LN(1 + COALESCE(f.favorite_count, 0)) * #{favoriteWeight}
                + LN(1 + COALESCE(c.comment_count, 0)) * #{commentWeight}
                - GREATEST(0, EXTRACT(EPOCH FROM (NOW() - v.created_at)) / 3600.0) * #{ageDecay}
            ) DESC, v.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<VideoDiscoveryRow> findTrending(
            @Param("limit") int limit,
            @Param("offset") int offset,
            @Param("viewWeight") double viewWeight,
            @Param("likeWeight") double likeWeight,
            @Param("favoriteWeight") double favoriteWeight,
            @Param("commentWeight") double commentWeight,
            @Param("ageDecay") double ageDecay
    );
}
