package com.kiki.video.api.notification.mapper;

import com.kiki.video.api.notification.model.Notification;
import com.kiki.video.api.notification.model.NotificationRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("""
            INSERT INTO notifications (
                recipient_user_id, actor_user_id, type, video_id, comment_id, parent_comment_id,
                is_read, created_at, read_at
            ) VALUES (
                #{recipientUserId}, #{actorUserId}, #{type}, #{videoId}, #{commentId}, #{parentCommentId},
                #{read}, #{createdAt}, #{readAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Notification notification);

    @Select("""
            SELECT n.id,
                   n.type,
                   n.is_read AS read,
                   n.created_at,
                   n.actor_user_id,
                   a.username AS actor_username,
                   a.display_name AS actor_display_name,
                   n.video_id,
                   v.title AS video_title,
                   n.comment_id,
                   c.content AS comment_content,
                   n.parent_comment_id
            FROM notifications n
            LEFT JOIN users a ON a.id = n.actor_user_id
            LEFT JOIN videos v ON v.id = n.video_id
            LEFT JOIN comments c ON c.id = n.comment_id
            WHERE n.recipient_user_id = #{recipientUserId}
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<NotificationRow> findPageByRecipient(
            @Param("recipientUserId") long recipientUserId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT COUNT(*)
            FROM notifications
            WHERE recipient_user_id = #{recipientUserId}
            """)
    long countByRecipient(@Param("recipientUserId") long recipientUserId);

    @Select("""
            SELECT COUNT(*)
            FROM notifications
            WHERE recipient_user_id = #{recipientUserId}
              AND is_read = FALSE
            """)
    long countUnreadByRecipient(@Param("recipientUserId") long recipientUserId);

    @Select("""
            SELECT id, recipient_user_id, actor_user_id, type, video_id, comment_id, parent_comment_id,
                   is_read AS read, created_at, read_at
            FROM notifications
            WHERE id = #{id}
            """)
    Notification findById(@Param("id") long id);

    @Update("""
            UPDATE notifications
            SET is_read = TRUE, read_at = NOW()
            WHERE id = #{id}
              AND recipient_user_id = #{recipientUserId}
              AND is_read = FALSE
            """)
    int markRead(@Param("id") long id, @Param("recipientUserId") long recipientUserId);

    @Update("""
            UPDATE notifications
            SET is_read = TRUE, read_at = NOW()
            WHERE recipient_user_id = #{recipientUserId}
              AND is_read = FALSE
            """)
    int markAllRead(@Param("recipientUserId") long recipientUserId);
}
