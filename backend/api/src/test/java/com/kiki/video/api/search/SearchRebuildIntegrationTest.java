package com.kiki.video.api.search;

import com.kiki.video.api.search.dto.SearchRebuildReport;
import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.mapper.SearchVideoMapper;
import com.kiki.video.api.search.service.SearchRebuildService;
import com.kiki.video.api.support.AbstractSearchIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchRebuildIntegrationTest extends AbstractSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SearchRebuildService searchRebuildService;

    @Autowired
    private SearchVideoMapper searchVideoMapper;

    @Autowired
    private VideoSearchIndex videoSearchIndex;

    @Test
    void rebuildIndexesEligibleVideosIdempotently() throws Exception {
        String token = registerAndLogin(unique("rebuild"));
        long first = upload(token, "Rebuild Alpha Unique");
        long second = upload(token, "Rebuild Beta Unique");

        SearchRebuildReport firstReport = searchRebuildService.rebuild();
        videoSearchIndex.refresh();
        long eligible = searchVideoMapper.countEligible();
        assertThat(firstReport.failed()).isZero();
        assertThat(firstReport.indexed()).isEqualTo(eligible);
        assertThat(videoSearchIndex.count()).isEqualTo(eligible);

        SearchRebuildReport secondReport = searchRebuildService.rebuild();
        videoSearchIndex.refresh();
        assertThat(secondReport.failed()).isZero();
        assertThat(secondReport.indexed()).isEqualTo(eligible);
        assertThat(videoSearchIndex.count()).isEqualTo(eligible);

        mockMvc.perform(get("/api/search/videos").param("q", "Rebuild Alpha Unique"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) first));
        mockMvc.perform(get("/api/search/videos").param("q", "Rebuild Beta Unique"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) second));
    }

    private long upload(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", fixture()))
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
