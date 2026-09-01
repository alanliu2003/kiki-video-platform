package com.kiki.video.api.user;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PublicProfileIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void publicProfileReturnsPublicFieldsOnly() throws Exception {
        String username = unique("pubowner");
        String token = registerAndLogin(username);
        long userId = userId(username);
        upload(token, "Owner clip");

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.displayName").value(username))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.publicVideoCount").value(1))
                .andExpect(jsonPath("$.totalViews").value(0))
                .andExpect(jsonPath("$.followedByCurrentUser").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void publicProfileIs404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/users/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User was not found"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void currentUserRemainsPrivateAndAuthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String username = unique("meonly");
        String token = registerAndLogin(username);
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void authenticatedVisitorSeesFollowStateAndCanToggle() throws Exception {
        String creatorName = unique("creator");
        String visitorName = unique("visitor");
        registerAndLogin(creatorName);
        String visitorToken = registerAndLogin(visitorName);
        long creatorId = userId(creatorName);

        mockMvc.perform(get("/api/users/" + creatorId).header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByCurrentUser").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));

        mockMvc.perform(put("/api/users/" + creatorId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByCurrentUser").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        mockMvc.perform(get("/api/users/" + creatorId).header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByCurrentUser").value(true))
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(0));

        mockMvc.perform(get("/api/users/" + userId(visitorName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followingCount").value(1));

        mockMvc.perform(delete("/api/users/" + creatorId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByCurrentUser").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));

        mockMvc.perform(get("/api/users/" + creatorId).header(HttpHeaders.AUTHORIZATION, bearer(visitorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followedByCurrentUser").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));
    }

    @Test
    void selfProfileDoesNotClaimToBeFollowed() throws Exception {
        String username = unique("selfprof");
        String token = registerAndLogin(username);
        long userId = userId(username);

        mockMvc.perform(get("/api/users/" + userId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.followedByCurrentUser").value(false))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void userVideosAreNewestFirstAndPaginated() throws Exception {
        String username = unique("vidowner");
        String token = registerAndLogin(username);
        long userId = userId(username);
        long older = upload(token, "Older clip");
        long newer = upload(token, "Newer clip");
        jdbcTemplate.update("UPDATE videos SET view_count = 7 WHERE id = ?", newer);

        mockMvc.perform(get("/api/users/" + userId + "/videos").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value((int) newer))
                .andExpect(jsonPath("$.items[0].title").value("Newer clip"))
                .andExpect(jsonPath("$.items[0].viewCount").value(7))
                .andExpect(jsonPath("$.items[0].owner.username").value(username))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").hasJsonPath());

        mockMvc.perform(get("/api/users/" + userId + "/videos").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value((int) older));

        mockMvc.perform(get("/api/users/" + userId + "/videos").param("page", "-2").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicVideoCount").value(2))
                .andExpect(jsonPath("$.totalViews").value(7));
    }

    @Test
    void userVideos404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/users/999999999/videos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private long upload(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(mp4("demo.mp4"))
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
        return id == null ? -1L : id;
    }

    private static MockMultipartFile mp4(String filename) {
        return new MockMultipartFile("file", filename, "video/mp4", FIXTURE);
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
