package com.kiki.video.api.interaction;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractionIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unauthenticatedLikeIsRejected() throws Exception {
        long videoId = upload(registerAndLogin(unique("owner")), "Public video");
        mockMvc.perform(put("/api/videos/" + videoId + "/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void likeIsIdempotentAndUnlikeIsSafe() throws Exception {
        String owner = unique("likeowner");
        String viewer = unique("liker");
        long videoId = upload(registerAndLogin(owner), "Like target");
        String token = registerAndLogin(viewer);

        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByCurrentUser").value(true));

        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByCurrentUser").value(true));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_likes WHERE video_id = ?",
                Integer.class,
                videoId
        );
        assertThat(rows).isEqualTo(1);

        mockMvc.perform(delete("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByCurrentUser").value(false));

        mockMvc.perform(delete("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByCurrentUser").value(false));
    }

    @Test
    void favoriteIsIdempotentAndUnfavoriteIsSafe() throws Exception {
        long videoId = upload(registerAndLogin(unique("favowner")), "Favorite target");
        String token = registerAndLogin(unique("faver"));

        mockMvc.perform(put("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteCount").value(1))
                .andExpect(jsonPath("$.favoritedByCurrentUser").value(true));
        mockMvc.perform(put("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteCount").value(1));
        mockMvc.perform(delete("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteCount").value(0))
                .andExpect(jsonPath("$.favoritedByCurrentUser").value(false));
        mockMvc.perform(delete("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteCount").value(0));
    }

    @Test
    void followIsIdempotentSelfFollowRejectedAndUnfollowIsSafe() throws Exception {
        String creatorName = unique("creator");
        String followerName = unique("follower");
        String creatorToken = registerAndLogin(creatorName);
        String followerToken = registerAndLogin(followerName);
        long creatorId = userId(creatorName);
        long followerId = userId(followerName);

        mockMvc.perform(put("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(followerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followedByCurrentUser").value(true));
        mockMvc.perform(put("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(followerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1));

        mockMvc.perform(put("/api/users/" + followerId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(followerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_FOLLOW_NOT_ALLOWED"));

        mockMvc.perform(delete("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(followerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followedByCurrentUser").value(false));
        mockMvc.perform(delete("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(followerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(0));

        mockMvc.perform(get("/api/users/" + creatorId + "/relationship")
                        .header(HttpHeaders.AUTHORIZATION, bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followedByCurrentUser").value(false));
    }

    @Test
    void commentsSupportCreateReplyPaginationAndAnonymousRead() throws Exception {
        String authorName = unique("commenter");
        String replierName = unique("replier");
        long videoId = upload(registerAndLogin(unique("vidowner")), "Comment target");
        String authorToken = registerAndLogin(authorName);
        String replierToken = registerAndLogin(replierName);

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   Great video   \"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   Great video   \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Great video"))
                .andExpect(jsonPath("$.author.username").value(authorName))
                .andExpect(jsonPath("$.parentCommentId").isEmpty())
                .andReturn();
        long parentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(replierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Agreed\",\"parentCommentId\":" + parentId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCommentId").value(parentId))
                .andExpect(jsonPath("$.content").value("Agreed"));

        long otherVideoId = upload(registerAndLogin(unique("otherowner")), "Other video");
        mockMvc.perform(post("/api/videos/" + otherVideoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(replierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Wrong video\",\"parentCommentId\":" + parentId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COMMENT_PARENT"));

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(authorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Second comment\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/videos/" + videoId + "/comments?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].content").value("Second comment"));

        mockMvc.perform(get("/api/videos/" + videoId + "/comments?page=1&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].content").value("Great video"))
                .andExpect(jsonPath("$.items[0].replies.length()").value(1))
                .andExpect(jsonPath("$.items[0].replies[0].content").value("Agreed"));
    }

    @Test
    void anonymousSummaryHidesViewerStateAndCountsStayPublic() throws Exception {
        long videoId = upload(registerAndLogin(unique("sumowner")), "Summary video");
        String token = registerAndLogin(unique("sumviewer"));
        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Nice\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/videos/" + videoId + "/interactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.likedByCurrentUser").value(false))
                .andExpect(jsonPath("$.favoritedByCurrentUser").value(false));

        mockMvc.perform(get("/api/videos/" + videoId + "/interactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByCurrentUser").value(true));
    }

    @Test
    void missingVideoAndUserAreNotFound() throws Exception {
        String token = registerAndLogin(unique("missing"));
        mockMvc.perform(put("/api/videos/999999/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
        mockMvc.perform(put("/api/users/999999/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private long upload(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", FIXTURE))
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String registerAndLogin(String username) throws Exception {
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
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        assertThat(id).isNotNull();
        return id;
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
}
