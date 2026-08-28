package com.kiki.video.api.danmaku;

import com.kiki.video.api.danmaku.dto.DanmakuResponse;
import com.kiki.video.api.danmaku.dto.DanmakuUserResponse;
import com.kiki.video.api.danmaku.realtime.DanmakuFanout;
import com.kiki.video.api.danmaku.realtime.DanmakuRedisPublisher;
import com.kiki.video.api.danmaku.service.DanmakuService;
import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DanmakuRedisUnavailableIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DanmakuService danmakuService;

    @Autowired
    private DanmakuRedisPublisher publisher;

    @MockitoSpyBean
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private DanmakuFanout fanout;

    @AfterEach
    void resetSpies() {
        Mockito.reset(redis, fanout);
    }

    @Test
    void persistAndLocalFallbackSucceedWhenRedisPublishFails() throws Exception {
        long videoId = upload(registerAndLogin(unique("redisdownowner")), "Redis down video");
        String username = unique("redisdownuser");
        registerAndLogin(username);
        long userId = userId(username);

        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redis)
                .convertAndSend(anyString(), anyString());

        var result = danmakuService.submit(videoId, userId, "redis-down-1", "still saved", 2500L);
        assertThat(result.created()).isTrue();
        publisher.publishOrFallback(result.danmaku());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM danmaku WHERE id = ?",
                Integer.class,
                result.danmaku().id()
        );
        assertThat(rows).isEqualTo(1);
        verify(fanout).broadcastLocal(result.danmaku());

        mockMvc.perform(get("/api/videos/" + videoId + "/danmaku?fromMs=0&toMs=60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("still saved"));
    }

    @Test
    void rateLimitFailsOpenWhenRedisIsDown() throws Exception {
        long videoId = upload(registerAndLogin(unique("failopenowner")), "Fail open video");
        String username = unique("failopenuser");
        registerAndLogin(username);
        long userId = userId(username);
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redis)
                .opsForValue();

        for (int i = 0; i < 12; i++) {
            var result = danmakuService.submit(videoId, userId, "open-" + i, "msg " + i, 1000L + i);
            assertThat(result.created()).isTrue();
        }
    }

    @Test
    void publisherFallbackUsesCanonicalPayload() {
        DanmakuResponse danmaku = new DanmakuResponse(
                9L,
                3L,
                new DanmakuUserResponse(2L, "bob", "Bob"),
                "hello",
                1000L,
                "NORMAL",
                Instant.parse("2026-08-28T08:00:00Z")
        );
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redis)
                .convertAndSend(anyString(), anyString());
        publisher.publishOrFallback(danmaku);
        verify(fanout).broadcastLocal(danmaku);
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
