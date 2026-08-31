package com.kiki.video.api.view;

import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ViewTrackingRedisUnavailableIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StringRedisTemplate redis;

    @BeforeEach
    void redisIsDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(redis.hasKey(any())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(redis.keys(any())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(redis.delete(any(String.class))).thenThrow(new RedisConnectionFailureException("Redis down"));
    }

    @Test
    void qualifyAndTrendingStillWorkWhenRedisIsDown() throws Exception {
        long videoId = upload(registerAndLogin(unique("redisviewowner")), "Redis view");
        String clientViewId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/videos/" + videoId + "/views/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"watchedMs":10000,"clientViewId":"%s"}
                                """.formatted(clientViewId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.viewCount").value(1));

        mockMvc.perform(post("/api/videos/" + videoId + "/views/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"watchedMs":10000,"clientViewId":"%s"}
                                """.formatted(clientViewId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.alreadyCounted").value(true))
                .andExpect(jsonPath("$.viewCount").value(1));

        mockMvc.perform(get("/api/videos/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/videos/" + videoId + "/playback"))
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
