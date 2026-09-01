package com.kiki.video.api.user.mapper;

import com.kiki.video.api.user.dto.OwnerVideoStats;
import com.kiki.video.api.user.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Insert("""
            INSERT INTO users (
                username, email, password_hash, display_name, role, status, created_at, updated_at
            ) VALUES (
                #{username}, #{email}, #{passwordHash}, #{displayName}, #{role}, #{status},
                #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(User user);

    @Select("""
            SELECT id, username, email, password_hash, display_name, role, status, created_at, updated_at
            FROM users
            WHERE id = #{id}
            """)
    User findById(Long id);

    @Select("""
            SELECT id, username, email, password_hash, display_name, role, status, created_at, updated_at
            FROM users
            WHERE username = #{username}
            """)
    User findByUsername(String username);

    @Select("""
            SELECT id, username, email, password_hash, display_name, role, status, created_at, updated_at
            FROM users
            WHERE email = #{email}
            """)
    User findByEmail(String email);

    @Select("""
            SELECT id, username, email, password_hash, display_name, role, status, created_at, updated_at
            FROM users
            WHERE username = #{identifier} OR email = #{identifier}
            """)
    User findByUsernameOrEmail(String identifier);

    @Select("""
            SELECT COUNT(*)::bigint AS public_video_count,
                   COALESCE(SUM(view_count), 0)::bigint AS total_views
            FROM videos
            WHERE owner_user_id = #{ownerUserId}
            """)
    OwnerVideoStats videoStatsByOwner(Long ownerUserId);
}
