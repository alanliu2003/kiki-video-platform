package com.kiki.video.api.notification;

import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anonymousNotificationRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/notifications/1/read"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void firstLikeCreatesOneNotificationAndRepeatedLikeDoesNot() throws Exception {
        Session owner = register(unique("likeowner"));
        Session viewer = register(unique("liker"));
        long videoId = upload(owner.token, "Like target");

        like(viewer.token, videoId);
        like(viewer.token, videoId);

        assertThat(notificationCount(owner.userId, "VIDEO_LIKED")).isEqualTo(1);
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("VIDEO_LIKED"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].actor.id").value(viewer.userId))
                .andExpect(jsonPath("$.items[0].actor.username").value(viewer.username))
                .andExpect(jsonPath("$.items[0].actor.displayName").value(viewer.username))
                .andExpect(jsonPath("$.items[0].video.id").value(videoId))
                .andExpect(jsonPath("$.items[0].video.title").value("Like target"))
                .andExpect(jsonPath("$.items[0].video.thumbnailUrl").value("/api/videos/" + videoId + "/thumbnail"))
                .andExpect(jsonPath("$.items[0].comment").value(nullValue()));
    }

    @Test
    void selfLikeDoesNotCreateNotification() throws Exception {
        Session owner = register(unique("selflike"));
        long videoId = upload(owner.token, "Own video");
        like(owner.token, videoId);
        assertThat(notificationCount(owner.userId, "VIDEO_LIKED")).isZero();
        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void unlikeLeavesHistoricalNotificationAndRelikeCreatesAnother() throws Exception {
        Session owner = register(unique("unlikeowner"));
        Session viewer = register(unique("unliker"));
        long videoId = upload(owner.token, "Unlike target");

        like(viewer.token, videoId);
        mockMvc.perform(delete("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(viewer.token)))
                .andExpect(status().isOk());
        assertThat(notificationCount(owner.userId, "VIDEO_LIKED")).isEqualTo(1);

        like(viewer.token, videoId);
        assertThat(notificationCount(owner.userId, "VIDEO_LIKED")).isEqualTo(2);
    }

    @Test
    void firstFavoriteCreatesOneNotificationAndRepeatedFavoriteDoesNot() throws Exception {
        Session owner = register(unique("favowner"));
        Session viewer = register(unique("faver"));
        long videoId = upload(owner.token, "Favorite target");

        favorite(viewer.token, videoId);
        favorite(viewer.token, videoId);
        assertThat(notificationCount(owner.userId, "VIDEO_FAVORITED")).isEqualTo(1);
    }

    @Test
    void firstFollowCreatesOneNotificationAndRepeatedFollowDoesNot() throws Exception {
        Session creator = register(unique("creator"));
        Session follower = register(unique("follower"));

        follow(follower.token, creator.userId);
        follow(follower.token, creator.userId);
        assertThat(notificationCount(creator.userId, "USER_FOLLOWED")).isEqualTo(1);

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(creator.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("USER_FOLLOWED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(follower.userId))
                .andExpect(jsonPath("$.items[0].video").value(nullValue()))
                .andExpect(jsonPath("$.items[0].comment").value(nullValue()));
    }

    @Test
    void unfollowLeavesHistoricalNotificationAndRefollowCreatesAnother() throws Exception {
        Session creator = register(unique("unfowner"));
        Session follower = register(unique("unfollower"));

        follow(follower.token, creator.userId);
        mockMvc.perform(delete("/api/users/" + creator.userId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, bearer(follower.token)))
                .andExpect(status().isOk());
        assertThat(notificationCount(creator.userId, "USER_FOLLOWED")).isEqualTo(1);

        follow(follower.token, creator.userId);
        assertThat(notificationCount(creator.userId, "USER_FOLLOWED")).isEqualTo(2);
    }

    @Test
    void topLevelCommentNotifiesVideoOwnerAndSelfCommentDoesNot() throws Exception {
        Session owner = register(unique("cowner"));
        Session commenter = register(unique("cauthor"));
        long videoId = upload(owner.token, "Comment target");

        long commentId = comment(commenter.token, videoId, "Great video", null);
        comment(owner.token, videoId, "Thanks everyone", null);

        assertThat(notificationCount(owner.userId, "VIDEO_COMMENTED")).isEqualTo(1);
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("VIDEO_COMMENTED"))
                .andExpect(jsonPath("$.items[0].comment.id").value(commentId))
                .andExpect(jsonPath("$.items[0].comment.contentSnippet").value("Great video"))
                .andExpect(jsonPath("$.items[0].video.title").value("Comment target"));
    }

    @Test
    void replyNotifiesParentAuthorAndSelfReplyDoesNot() throws Exception {
        Session owner = register(unique("rown"));
        Session commenter = register(unique("rparent"));
        long videoId = upload(owner.token, "Reply target");
        long parentId = comment(commenter.token, videoId, "First thought", null);

        long replyId = comment(owner.token, videoId, "Thanks for watching", parentId);
        comment(commenter.token, videoId, "Adding more", parentId);

        assertThat(notificationCount(commenter.userId, "COMMENT_REPLIED")).isEqualTo(1);
        assertThat(notificationCount(commenter.userId, "VIDEO_COMMENTED")).isEqualTo(0);
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(commenter.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("COMMENT_REPLIED"))
                .andExpect(jsonPath("$.items[0].comment.id").value(replyId))
                .andExpect(jsonPath("$.items[0].comment.contentSnippet").value("Thanks for watching"));
    }

    @Test
    void commentSnippetIsTruncatedServerSide() throws Exception {
        Session owner = register(unique("snipowner"));
        Session commenter = register(unique("snipper"));
        long videoId = upload(owner.token, "Snippet target");
        String longContent = "x".repeat(180);
        comment(commenter.token, videoId, longContent, null);

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].comment.contentSnippet").value("x".repeat(120)));
    }

    @Test
    void markReadIsIdempotentAndScopedToRecipient() throws Exception {
        Session owner = register(unique("readowner"));
        Session other = register(unique("otherinbox"));
        Session viewer = register(unique("readliker"));
        long videoId = upload(owner.token, "Read target");
        like(viewer.token, videoId);

        long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE recipient_user_id = ?",
                Long.class,
                owner.userId
        );

        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(other.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(post("/api/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].read").value(true));
    }

    @Test
    void readAllIsIdempotentAndDoesNotAffectOtherUsers() throws Exception {
        Session owner = register(unique("allowner"));
        Session other = register(unique("allother"));
        Session viewer = register(unique("allviewer"));
        long ownerVideo = upload(owner.token, "Owner video");
        long otherVideo = upload(other.token, "Other video");
        like(viewer.token, ownerVideo);
        favorite(viewer.token, ownerVideo);
        like(viewer.token, otherVideo);

        mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(other.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void inboxIsNewestFirstWithBoundedDeterministicPages() throws Exception {
        Session owner = register(unique("pageowner"));
        Session first = register(unique("pageone"));
        Session second = register(unique("pagetwo"));
        Session third = register(unique("pagethree"));
        long videoId = upload(owner.token, "Page target");

        like(first.token, videoId);
        favorite(second.token, videoId);
        follow(third.token, owner.userId);

        mockMvc.perform(get("/api/notifications?page=0&size=2").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("USER_FOLLOWED"))
                .andExpect(jsonPath("$.items[1].type").value("VIDEO_FAVORITED"));

        mockMvc.perform(get("/api/notifications?page=1&size=2").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("VIDEO_LIKED"));

        mockMvc.perform(get("/api/notifications?size=200").header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.items.length()").value(3));

        JsonNode items = read(mockMvc.perform(get("/api/notifications?size=20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token)))
                .andExpect(status().isOk())
                .andReturn());
        long firstId = items.get("items").get(0).get("id").asLong();
        long secondId = items.get("items").get(1).get("id").asLong();
        long thirdId = items.get("items").get(2).get("id").asLong();
        assertThat(firstId).isGreaterThan(secondId);
        assertThat(secondId).isGreaterThan(thirdId);
    }

    @Test
    void flywayCreatedNotificationTableAndIndexes() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'notifications'",
                Integer.class
        );
        Integer createdIdx = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'notifications'
                  AND indexname = 'notifications_recipient_created_idx'
                """,
                Integer.class
        );
        Integer unreadIdx = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'notifications'
                  AND indexname = 'notifications_recipient_unread_idx'
                """,
                Integer.class
        );
        assertThat(tables).isEqualTo(1);
        assertThat(createdIdx).isEqualTo(1);
        assertThat(unreadIdx).isEqualTo(1);
    }

    private void like(String token, long videoId) throws Exception {
        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void favorite(String token, long videoId) throws Exception {
        mockMvc.perform(put("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void follow(String token, long userId) throws Exception {
        mockMvc.perform(put("/api/users/" + userId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private long comment(String token, long videoId, String content, Long parentCommentId) throws Exception {
        String body = parentCommentId == null
                ? "{\"content\":%s}".formatted(objectMapper.writeValueAsString(content))
                : "{\"content\":%s,\"parentCommentId\":%d}".formatted(
                        objectMapper.writeValueAsString(content),
                        parentCommentId
                );
        MvcResult result = mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    private long notificationCount(long recipientUserId, String type) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND type = ?",
                Integer.class,
                recipientUserId,
                type
        );
        return count == null ? 0 : count;
    }

    private long upload(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", FIXTURE))
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    private Session register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "StrongPassword123"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "%s",
                                  "password": "StrongPassword123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = read(result);
        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        return new Session(username, userId, body.get("accessToken").asString());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

    private static byte[] fixtureVideo() {
        byte[] bytes = new byte[2048];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) (i & 0xFF);
        }
        return bytes;
    }

    private record Session(String username, long userId, String token) {
    }
}
