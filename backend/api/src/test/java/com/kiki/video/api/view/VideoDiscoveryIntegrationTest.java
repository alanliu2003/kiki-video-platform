package com.kiki.video.api.view;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VideoDiscoveryIntegrationTest extends AbstractIntegrationTest {

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
    void recentOrdersByCreatedAtThenId() throws Exception {
        String token = registerAndLogin(unique("recentowner"));
        long older = upload(token, "Older recent");
        long newer = upload(token, "Newer recent");
        Instant base = Instant.now().minus(2, ChronoUnit.HOURS);
        jdbcTemplate.update("UPDATE videos SET created_at = ? WHERE id = ?", Timestamp.from(base), older);
        jdbcTemplate.update("UPDATE videos SET created_at = ? WHERE id = ?", Timestamp.from(base.plusSeconds(60)), newer);

        JsonNode items = objectMapper.readTree(
                mockMvc.perform(get("/api/videos/recent").param("size", "200"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].viewCount").exists())
                        .andExpect(jsonPath("$.items[0].owner.username").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("items");
        int newerIndex = indexOf(items, newer);
        int olderIndex = indexOf(items, older);
        assertThat(newerIndex).isLessThan(olderIndex);
    }

    @Test
    void trendingOrdersByDocumentedScore() throws Exception {
        flushTrendingCache();
        String ownerToken = registerAndLogin(unique("trendowner"));
        String liker = registerAndLogin(unique("trendliker"));
        Instant now = Instant.now();

        long highViews = upload(ownerToken, "High views");
        long liked = upload(ownerToken, "Liked video");
        long stale = upload(ownerToken, "Stale video");
        long freshLow = upload(ownerToken, "Fresh low");

        jdbcTemplate.update("UPDATE videos SET view_count = ?, created_at = ? WHERE id = ?",
                10_000, Timestamp.from(now.minus(1, ChronoUnit.HOURS)), highViews);
        jdbcTemplate.update("UPDATE videos SET view_count = ?, created_at = ? WHERE id = ?",
                100, Timestamp.from(now.minus(1, ChronoUnit.HOURS)), liked);
        jdbcTemplate.update("UPDATE videos SET view_count = ?, created_at = ? WHERE id = ?",
                10_000, Timestamp.from(now.minus(1000, ChronoUnit.HOURS)), stale);
        jdbcTemplate.update("UPDATE videos SET view_count = ?, created_at = ? WHERE id = ?",
                20, Timestamp.from(now), freshLow);

        mockMvc.perform(put("/api/videos/" + liked + "/like").header(HttpHeaders.AUTHORIZATION, bearer(liker)))
                .andExpect(status().isOk());

        JsonNode items = objectMapper.readTree(
                mockMvc.perform(get("/api/videos/trending").param("size", "200"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items[0].id").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("items");

        int highViewsIndex = indexOf(items, highViews);
        int likedIndex = indexOf(items, liked);
        int staleIndex = indexOf(items, stale);
        int freshIndex = indexOf(items, freshLow);
        assertThat(highViewsIndex).isLessThan(likedIndex);
        assertThat(likedIndex).isLessThan(freshIndex);
        assertThat(freshIndex).isLessThan(staleIndex);

        mockMvc.perform(get("/api/videos/" + highViews))
                .andExpect(jsonPath("$.viewCount").value(10_000));
    }

    @Test
    void trendingAndRecentRejectDeepInvalidPagesByBounding() throws Exception {
        mockMvc.perform(get("/api/videos/trending").param("page", "-3").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(500));
        mockMvc.perform(get("/api/videos/recent").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void qualifyThenRecentShowsUpdatedCount() throws Exception {
        long videoId = upload(registerAndLogin(unique("feedview")), "Feed view");
        qualify(videoId);
        mockMvc.perform(get("/api/videos/recent").param("size", "50"))
                .andExpect(status().isOk());
        JsonNode items = objectMapper.readTree(
                mockMvc.perform(get("/api/videos/recent").param("size", "50"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).get("items");
        JsonNode card = null;
        for (JsonNode item : items) {
            if (item.get("id").asLong() == videoId) {
                card = item;
                break;
            }
        }
        assertThat(card).isNotNull();
        assertThat(card.get("viewCount").asLong()).isEqualTo(1);
    }

    private void flushTrendingCache() {
        Set<String> keys = redis.keys("kiki:trending:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static int indexOf(JsonNode items, long videoId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).get("id").asLong() == videoId) {
                return i;
            }
        }
        throw new AssertionError("video " + videoId + " missing from feed");
    }

    private void qualify(long videoId) throws Exception {
        mockMvc.perform(post("/api/videos/" + videoId + "/views/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"watchedMs":10000,"clientViewId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
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
