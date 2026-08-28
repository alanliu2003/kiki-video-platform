package com.kiki.video.api.interaction;

import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

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
class InteractionRedisIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void cacheMissLoadsFromPostgresAndPopulatesRedis() throws Exception {
        long videoId = upload(registerAndLogin(unique("cacheowner")), "Cache video");
        String token = registerAndLogin(unique("cacheliker"));
        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        redis.delete(RedisKeys.likeCount(videoId));

        mockMvc.perform(get("/api/videos/" + videoId + "/interactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(redis.opsForValue().get(RedisKeys.likeCount(videoId))).isEqualTo("1");
        assertThat(redis.getExpire(RedisKeys.likeCount(videoId), TimeUnit.SECONDS)).isGreaterThan(0);
    }

    @Test
    void likeCountStaysConsistentAcrossIdempotentWrites() throws Exception {
        long videoId = upload(registerAndLogin(unique("idempowner")), "Idempotent likes");
        String token = registerAndLogin(unique("idempliker"));

        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertDbAndRedisLikeCount(videoId, 1);

        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertDbAndRedisLikeCount(videoId, 1);

        mockMvc.perform(delete("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertDbAndRedisLikeCount(videoId, 0);

        mockMvc.perform(delete("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertDbAndRedisLikeCount(videoId, 0);
        assertThat(Long.parseLong(redis.opsForValue().get(RedisKeys.likeCount(videoId)))).isZero();
    }

    @Test
    void favoriteAndFollowCountsStayConsistent() throws Exception {
        String creator = unique("rediscreator");
        long videoId = upload(registerAndLogin(creator), "Fav follow");
        String token = registerAndLogin(unique("redisfollower"));
        long creatorId = userId(creator);

        mockMvc.perform(put("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertThat(dbCount("SELECT COUNT(*) FROM video_favorites WHERE video_id = ?", videoId)).isEqualTo(1);
        assertThat(redis.opsForValue().get(RedisKeys.favoriteCount(videoId))).isEqualTo("1");

        mockMvc.perform(put("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertThat(dbCount("SELECT COUNT(*) FROM user_follows WHERE followed_user_id = ?", creatorId)).isEqualTo(1);
        assertThat(redis.opsForValue().get(RedisKeys.followerCount(creatorId))).isEqualTo("1");

        mockMvc.perform(delete("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + creatorId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        assertThat(redis.opsForValue().get(RedisKeys.followerCount(creatorId))).isEqualTo("0");
    }

    @Test
    void commentCountIncrementsOnceAndStoresNoSensitivePayload() throws Exception {
        long videoId = upload(registerAndLogin(unique("cmtowner")), "Comment cache");
        String token = registerAndLogin(unique("cmtuser"));
        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/videos/" + videoId + "/interactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(1));
        assertThat(redis.opsForValue().get(RedisKeys.commentCount(videoId))).isEqualTo("1");
        assertThat(redis.keys("kiki:*")).allSatisfy(key -> {
            String value = redis.opsForValue().get(key);
            assertThat(value).doesNotContain("Bearer");
            assertThat(value).doesNotContain("Hello");
            assertThat(value).doesNotContain("@example.com");
        });
    }

    private void assertDbAndRedisLikeCount(long videoId, long expected) {
        assertThat(dbCount("SELECT COUNT(*) FROM video_likes WHERE video_id = ?", videoId)).isEqualTo(expected);
        String cached = redis.opsForValue().get(RedisKeys.likeCount(videoId));
        if (cached != null) {
            assertThat(Long.parseLong(cached)).isEqualTo(expected);
        }
        mockMvcGetLikeCount(videoId, expected);
    }

    private void mockMvcGetLikeCount(long videoId, long expected) {
        try {
            mockMvc.perform(get("/api/videos/" + videoId + "/interactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(expected));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private long dbCount(String sql, long id) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
        assertThat(count).isNotNull();
        return count;
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
