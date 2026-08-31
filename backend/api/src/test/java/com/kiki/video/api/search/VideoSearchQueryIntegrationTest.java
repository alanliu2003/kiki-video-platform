package com.kiki.video.api.search;

import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.outbox.SearchIndexOutboxPublisher;
import com.kiki.video.api.support.AbstractSearchIntegrationTest;
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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VideoSearchQueryIntegrationTest extends AbstractSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SearchIndexOutboxPublisher publisher;

    @Autowired
    private VideoSearchIndex videoSearchIndex;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchesTitleDescriptionCreatorAndRejectsUnrelatedQuery() throws Exception {
        String token = unique("qtitle");
        String username = unique("quser");
        String displayName = "Display " + unique("Qname");
        String auth = registerAndLogin(username);
        jdbcTemplate.update("UPDATE users SET display_name = ? WHERE username = ?", displayName, username);

        long titleId = upload(auth, token + " ExactTitleHit", "neutral description");
        long descriptionId = upload(auth, "Unrelated Card Title " + unique("card"), token + " lives only in description");
        project();

        mockMvc.perform(get("/api/search/videos").param("q", token + " ExactTitleHit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) titleId))
                .andExpect(jsonPath("$.items[0].highlights.title").isArray());

        mockMvc.perform(get("/api/search/videos").param("q", token + " lives only in description"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) descriptionId));

        mockMvc.perform(get("/api/search/videos").param("q", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/search/videos").param("q", displayName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/search/videos").param("q", "zxqnohit" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(get("/api/search/videos").param("q", token + " ExactTitleHit").param("ownerId", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void paginationNewestSortFiltersHighlightsAndTypoMatch() throws Exception {
        String marker = unique("qpage");
        String auth = registerAndLogin(unique("qpager"));
        long older = upload(auth, marker + " Alpha", "first");
        Thread.sleep(20);
        long newer = upload(auth, marker + " Beta", "second");
        long ready = upload(auth, marker + " ReadyClip", "ready");
        jdbcTemplate.update(
                """
                UPDATE media_objects
                SET processing_status = 'READY', thumbnail_key = 'processed/1/thumbnail.jpg'
                WHERE id = (SELECT media_object_id FROM videos WHERE id = ?)
                """,
                ready
        );
        project();

        mockMvc.perform(get("/api/search/videos")
                        .param("q", marker)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "NEWEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].videoId").value((int) newer));

        mockMvc.perform(get("/api/search/videos")
                        .param("q", marker)
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "NEWEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/search/videos")
                        .param("q", marker)
                        .param("sort", "OLDEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) older));

        mockMvc.perform(get("/api/search/videos")
                        .param("q", marker)
                        .param("processingStatus", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) ready))
                .andExpect(jsonPath("$.items[0].processingStatus").value("READY"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("/api/videos/" + ready + "/thumbnail"));

        mockMvc.perform(get("/api/search/videos").param("q", marker + " Alfa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) older));
    }

    @Test
    void failedAndLegacyVideosRemainSearchable() throws Exception {
        String marker = unique("qelig");
        String auth = registerAndLogin(unique("qeliguser"));
        long failedId = upload(auth, marker + " FailedRaw", "still public");
        jdbcTemplate.update(
                """
                UPDATE media_objects
                SET processing_status = 'FAILED'
                WHERE id = (SELECT media_object_id FROM videos WHERE id = ?)
                """,
                failedId
        );
        project();

        mockMvc.perform(get("/api/search/videos").param("q", marker + " FailedRaw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) failedId))
                .andExpect(jsonPath("$.items[0].processingStatus").value("FAILED"));
    }

    private void project() {
        publisher.publishDue();
        videoSearchIndex.refresh();
    }

    private long upload(String token, String title, String description) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", fixture()))
                        .param("title", title)
                        .param("description", description)
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

    private static byte[] fixture() {
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
