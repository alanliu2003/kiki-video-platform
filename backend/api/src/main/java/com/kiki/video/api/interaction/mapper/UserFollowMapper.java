package com.kiki.video.api.interaction.mapper;

import com.kiki.video.api.interaction.model.UserFollow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserFollowMapper {

    @Insert("""
            INSERT INTO user_follows (follower_user_id, followed_user_id, created_at)
            VALUES (#{followerUserId}, #{followedUserId}, #{createdAt})
            ON CONFLICT (follower_user_id, followed_user_id) DO NOTHING
            """)
    int insertIgnore(UserFollow follow);

    @Delete("""
            DELETE FROM user_follows
            WHERE follower_user_id = #{followerUserId} AND followed_user_id = #{followedUserId}
            """)
    int delete(
            @Param("followerUserId") Long followerUserId,
            @Param("followedUserId") Long followedUserId
    );

    @Select("""
            SELECT COUNT(*)
            FROM user_follows
            WHERE followed_user_id = #{followedUserId}
            """)
    long countFollowers(Long followedUserId);

    @Select("""
            SELECT COUNT(*)
            FROM user_follows
            WHERE follower_user_id = #{followerUserId}
            """)
    long countFollowing(Long followerUserId);

    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM user_follows
                WHERE follower_user_id = #{followerUserId} AND followed_user_id = #{followedUserId}
            )
            """)
    boolean exists(
            @Param("followerUserId") Long followerUserId,
            @Param("followedUserId") Long followedUserId
    );
}
