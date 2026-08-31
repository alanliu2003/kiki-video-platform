package com.kiki.video.api.search.mapper;

import com.kiki.video.api.search.model.SearchVideoRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SearchVideoMapper {

    String SEARCH_VIDEO_COLUMNS = """
            SELECT v.id AS video_id,
                   v.title,
                   v.description,
                   v.owner_user_id AS owner_id,
                   u.username AS owner_username,
                   u.display_name AS owner_display_name,
                   v.status,
                   COALESCE(m.processing_status, 'NOT_REQUESTED') AS processing_status,
                   v.created_at,
                   m.duration_seconds,
                   (m.thumbnail_key IS NOT NULL) AS thumbnail_available
            FROM videos v
            JOIN users u ON u.id = v.owner_user_id
            LEFT JOIN media_objects m ON m.id = v.media_object_id
            """;

    @Select(SEARCH_VIDEO_COLUMNS + " WHERE v.id = #{videoId}")
    SearchVideoRow findByVideoId(Long videoId);

    @Select(SEARCH_VIDEO_COLUMNS + """
            WHERE v.id > #{afterId}
            ORDER BY v.id
            LIMIT #{limit}
            """)
    List<SearchVideoRow> findAfterId(@Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM videos")
    long countEligible();
}
