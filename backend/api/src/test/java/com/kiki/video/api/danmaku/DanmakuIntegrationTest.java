package com.kiki.video.api.danmaku;

import com.kiki.video.api.danmaku.service.DanmakuService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DanmakuIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DanmakuService danmakuService;

    @Test
    void historicalWindowReturnsOnlyMatchingRows() throws Exception {
        long videoId = upload(registerAndLogin(unique("histowner")), "History video");
        String username = unique("histuser");
        registerAndLogin(username);
        long userId = userId(username);
        danmakuService.submit(videoId, userId, "m1", "early", 1000L);
        danmakuService.submit(videoId, userId, "m2", "inside", 15000L);
        danmakuService.submit(videoId, userId, "m3", "late", 70000L);

        mockMvc.perform(get("/api/videos/" + videoId + "/danmaku?fromMs=10000&toMs=20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("inside"))
                .andExpect(jsonPath("$[0].videoTimeMs").value(15000))
                .andExpect(jsonPath("$[0].user.username").value(username));

        mockMvc.perform(get("/api/videos/" + videoId + "/danmaku?fromMs=0&toMs=90000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DANMAKU_WINDOW"));
    }

    @Test
    void invalidVideoAndWindowAreRejected() throws Exception {
        mockMvc.perform(get("/api/videos/999999/danmaku?fromMs=0&toMs=1000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
        long videoId = upload(registerAndLogin(unique("winowner")), "Window video");
        mockMvc.perform(get("/api/videos/" + videoId + "/danmaku?fromMs=-1&toMs=1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DANMAKU_WINDOW"));
        mockMvc.perform(get("/api/videos/" + videoId + "/danmaku?fromMs=10&toMs=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DANMAKU_WINDOW"));
    }

    @Test
    void submitPersistsAndRejectsInvalidPayloads() throws Exception {
        long videoId = upload(registerAndLogin(unique("persistowner")), "Persist video");
        String username = unique("persistsender");
        registerAndLogin(username);
        long userId = userId(username);

        var created = danmakuService.submit(videoId, userId, "client-1", "  Hello  ", 12345L);
        assertThat(created.created()).isTrue();
        assertThat(created.danmaku().content()).isEqualTo("Hello");
        assertThat(created.danmaku().videoTimeMs()).isEqualTo(12345L);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM danmaku WHERE video_id = ? AND client_message_id = ?",
                Integer.class,
                videoId,
                "client-1"
        );
        assertThat(rows).isEqualTo(1);

        var replay = danmakuService.submit(videoId, userId, "client-1", "Hello again", 12345L);
        assertThat(replay.created()).isFalse();
        assertThat(replay.danmaku().id()).isEqualTo(created.danmaku().id());
        Integer stillOne = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM danmaku WHERE user_id = ? AND client_message_id = ?",
                Integer.class,
                userId,
                "client-1"
        );
        assertThat(stillOne).isEqualTo(1);

        assertThatThrownBy(() -> danmakuService.submit(999999L, userId, "x", "hi", 0L))
                .hasMessageContaining("Video was not found");
        assertThatThrownBy(() -> danmakuService.submit(videoId, userId, "neg", "hi", -1L))
                .hasMessageContaining("videoTimeMs");
        assertThatThrownBy(() -> danmakuService.submit(videoId, userId, "blank", "   ", 0L))
                .hasMessageContaining("Content is required");
        assertThatThrownBy(() -> danmakuService.submit(videoId, userId, "long", "x".repeat(201), 0L))
                .hasMessageContaining("at most 200");
        assertThatThrownBy(() -> danmakuService.submit(videoId, userId, "legacy", "too far", 6L * 60 * 60 * 1000 + 1))
                .hasMessageContaining("legacy timestamp");
    }

    @Test
    void knownDurationRejectsTimestampPastEnd() throws Exception {
        long videoId = upload(registerAndLogin(unique("durowner")), "Duration video", uniqueFixture());
        Long mediaObjectId = jdbcTemplate.queryForObject(
                "SELECT media_object_id FROM videos WHERE id = ?",
                Long.class,
                videoId
        );
        jdbcTemplate.update("UPDATE media_objects SET duration_seconds = 10 WHERE id = ?", mediaObjectId);
        String username = unique("dursender");
        registerAndLogin(username);
        long userId = userId(username);

        var ok = danmakuService.submit(videoId, userId, "near-end", "ok", 11_500L);
        assertThat(ok.created()).isTrue();
        assertThatThrownBy(() -> danmakuService.submit(videoId, userId, "past-end", "nope", 13_000L))
                .hasMessageContaining("video duration");
    }

    private long upload(String token, String title) throws Exception {
        return upload(token, title, FIXTURE);
    }

    private long upload(String token, String title, byte[] file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", file))
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

    private static byte[] uniqueFixture() {
        byte[] bytes = fixtureVideo();
        bytes[bytes.length - 1] = (byte) System.nanoTime();
        return bytes;
    }
}
