package com.kiki.video.api.view;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ViewTrackingIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void detailStartsAtZeroAndQualifiedViewIncrementsOnce() throws Exception {
        long videoId = upload(registerAndLogin(unique("viewowner")), "View start");

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(0));

        String clientViewId = UUID.randomUUID().toString();
        MvcResult first = qualify(videoId, clientViewId, 10_000, null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.alreadyCounted").value(false))
                .andExpect(jsonPath("$.viewCount").value(1))
                .andReturn();

        String cookie = first.getResponse().getCookie(ViewerIdentity.ANON_COOKIE).getValue();
        assertThat(cookie).isNotBlank();

        qualify(videoId, clientViewId, 10_000, null, cookie, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.alreadyCounted").value(true))
                .andExpect(jsonPath("$.viewCount").value(1));

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/users/me/videos").header(HttpHeaders.AUTHORIZATION, bearer(registerAndLogin(unique("otherviewer")))))
                .andExpect(status().isOk());
    }

    @Test
    void sameViewerDifferentClientViewIdIsDedupedWithinWindow() throws Exception {
        long videoId = upload(registerAndLogin(unique("dedupeowner")), "Dedupe");
        String cookie = UUID.randomUUID().toString();

        qualify(videoId, UUID.randomUUID().toString(), 10_000, null, cookie, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        qualify(videoId, UUID.randomUUID().toString(), 10_000, null, cookie, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(false))
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void secondViewerIncrementsAndAuthenticatedIdentityIsSeparate() throws Exception {
        long videoId = upload(registerAndLogin(unique("twoowner")), "Two viewers");
        String viewerA = UUID.randomUUID().toString();
        String viewerB = UUID.randomUUID().toString();
        String token = registerAndLogin(unique("authviewer"));

        qualify(videoId, UUID.randomUUID().toString(), 10_000, null, viewerA, null)
                .andExpect(jsonPath("$.viewCount").value(1));
        qualify(videoId, UUID.randomUUID().toString(), 10_000, null, viewerB, null)
                .andExpect(jsonPath("$.viewCount").value(2));
        qualify(videoId, UUID.randomUUID().toString(), 10_000, null, viewerA, token)
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.viewCount").value(3));
    }

    @Test
    void logicalVideosSharingMediaHaveIndependentCounts() throws Exception {
        String tokenA = registerAndLogin(unique("shareA"));
        String tokenB = registerAndLogin(unique("shareB"));
        long videoA = upload(tokenA, "Shared media A");
        long videoB = upload(tokenB, "Shared media B");

        Long mediaA = jdbcTemplate.queryForObject("SELECT media_object_id FROM videos WHERE id = ?", Long.class, videoA);
        Long mediaB = jdbcTemplate.queryForObject("SELECT media_object_id FROM videos WHERE id = ?", Long.class, videoB);
        assertThat(mediaA).isEqualTo(mediaB);

        qualify(videoA, UUID.randomUUID().toString(), 10_000, null, UUID.randomUUID().toString(), null)
                .andExpect(jsonPath("$.viewCount").value(1));
        qualify(videoB, UUID.randomUUID().toString(), 10_000, null, UUID.randomUUID().toString(), null)
                .andExpect(jsonPath("$.viewCount").value(1));

        mockMvc.perform(get("/api/videos/" + videoA)).andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/videos/" + videoB)).andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void usesAuthoritativeDurationForShortVideosAndRejectsBelowThreshold() throws Exception {
        long videoId = upload(registerAndLogin(unique("shortowner")), "Short");
        jdbcTemplate.update(
                "UPDATE media_objects SET duration_seconds = 8 WHERE id = (SELECT media_object_id FROM videos WHERE id = ?)",
                videoId
        );

        qualify(videoId, UUID.randomUUID().toString(), 1_999, 60_000L, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIEW_NOT_QUALIFIED"));

        qualify(videoId, UUID.randomUUID().toString(), 2_000, 60_000L, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(true))
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void rejectsUnknownVideoAndMalformedPayload() throws Exception {
        qualify(999999L, UUID.randomUUID().toString(), 10_000, null, null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));

        mockMvc.perform(post("/api/videos/1/views/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/videos/1/views/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"watchedMs":-3,"clientViewId":"not-a-uuid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.ResultActions qualify(
            long videoId,
            String clientViewId,
            long watchedMs,
            Long durationMs,
            String anonCookie,
            String token
    ) throws Exception {
        var request = post("/api/videos/" + videoId + "/views/qualify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(clientViewId, watchedMs, durationMs));
        if (anonCookie != null) {
            request.cookie(new jakarta.servlet.http.Cookie(ViewerIdentity.ANON_COOKIE, anonCookie));
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(request);
    }

    private static String payload(String clientViewId, long watchedMs, Long durationMs) {
        if (durationMs == null) {
            return """
                    {"watchedMs":%d,"clientViewId":"%s"}
                    """.formatted(watchedMs, clientViewId);
        }
        return """
                {"watchedMs":%d,"durationMs":%d,"clientViewId":"%s"}
                """.formatted(watchedMs, durationMs, clientViewId);
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
