package com.kiki.video.api.danmaku.mapper;

import com.kiki.video.api.danmaku.model.Danmaku;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DanmakuMapper {

    @Insert("""
            INSERT INTO danmaku (
                video_id, user_id, content, video_time_ms, style, status, client_message_id, created_at
            ) VALUES (
                #{videoId}, #{userId}, #{content}, #{videoTimeMs}, #{style}, #{status},
                #{clientMessageId}, #{createdAt}
            )
            ON CONFLICT (user_id, client_message_id) DO NOTHING
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Danmaku danmaku);

    @Select("""
            SELECT d.id, d.video_id, d.user_id, d.content, d.video_time_ms, d.style, d.status,
                   d.client_message_id, d.created_at, u.username, u.display_name
            FROM danmaku d
            JOIN users u ON u.id = d.user_id
            WHERE d.user_id = #{userId}
              AND d.client_message_id = #{clientMessageId}
            """)
    Danmaku findByUserAndClientMessageId(
            @Param("userId") Long userId,
            @Param("clientMessageId") String clientMessageId
    );

    @Select("""
            SELECT d.id, d.video_id, d.user_id, d.content, d.video_time_ms, d.style, d.status,
                   d.client_message_id, d.created_at, u.username, u.display_name
            FROM danmaku d
            JOIN users u ON u.id = d.user_id
            WHERE d.video_id = #{videoId}
              AND d.status = 'ACTIVE'
              AND d.video_time_ms >= #{fromMs}
              AND d.video_time_ms < #{toMs}
            ORDER BY d.video_time_ms ASC, d.id ASC
            """)
    List<Danmaku> findActiveInWindow(
            @Param("videoId") Long videoId,
            @Param("fromMs") long fromMs,
            @Param("toMs") long toMs
    );
}
