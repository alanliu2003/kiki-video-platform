package com.kiki.video.api.search;

import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.mapper.SearchIndexOutboxMapper;
import com.kiki.video.api.search.model.SearchIndexOutbox;
import com.kiki.video.api.search.outbox.SearchIndexOutboxPublisher;
import com.kiki.video.api.search.service.SearchIndexRequestService;
import com.kiki.video.api.support.AbstractSearchIntegrationTest;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.common.search.VideoSearchIndexEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.elasticsearch.video-index-alias=kiki-videos-indexing-it",
        "app.elasticsearch.video-index-version=kiki-videos-indexing-it-v1"
})
class VideoSearchIndexingIntegrationTest extends AbstractSearchIntegrationTest {

    private static final int CHUNK_SIZE = 256 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SearchIndexOutboxMapper outboxMapper;

    @Autowired
    private SearchIndexOutboxPublisher publisher;

    @Autowired
    private SearchIndexRequestService searchIndexRequestService;

    @Autowired
    private VideoSearchIndex videoSearchIndex;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private MediaObjectMapper mediaObjectMapper;

    @Test
    void legacyUploadCreatesSearchOutboxAndIndexesOneDocument() throws Exception {
        String token = registerAndLogin(unique("legacysearch"));
        String title = unique("legacyidx") + " LegacySearchable";
        long videoId = legacyUpload(token, title, "only in this description");

        SearchIndexOutbox pending = outboxMapper.findLatestByVideoId(videoId);
        assertThat(pending).isNotNull();
        assertThat(pending.getEventType()).isEqualTo(VideoSearchIndexEvent.UPSERT);

        publisher.publishDue();
        videoSearchIndex.refresh();

        mockMvc.perform(get("/api/search/videos").param("q", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].videoId").value((int) videoId));
    }

    @Test
    void duplicateUpsertEventsProduceOneDocument() throws Exception {
        String token = registerAndLogin(unique("dupsearch"));
        String title = unique("dupididx") + " IdempotentSearch";
        long videoId = legacyUpload(token, title, null);
        publisher.publishDue();
        searchIndexRequestService.enqueueUpsert(videoId);
        publisher.publishDue();
        videoSearchIndex.refresh();

        mockMvc.perform(get("/api/search/videos").param("q", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void deduplicatedPhysicalUploadCreatesSeparateLogicalSearchDocuments() throws Exception {
        byte[] file = fixture(600_000, 11);
        String hash = sha256(file);
        String alice = registerAndLogin(unique("dedupealice"));
        long firstId = completeResumable(alice, file, hash, "First Logical Search Video", "shared bytes");

        String bob = registerAndLogin(unique("dedupebob"));
        JsonNode init = init(bob, file.length, hash);
        assertThat(init.get("deduplicated").asBoolean()).isTrue();
        long secondId = complete(bob, init.get("uploadId").asString(), "Second Logical Search Video", "shared bytes");

        Video first = videoMapper.findById(firstId);
        Video second = videoMapper.findById(secondId);
        assertThat(first.getMediaObjectId()).isEqualTo(second.getMediaObjectId());
        assertThat(mediaObjectMapper.findById(first.getMediaObjectId())).isNotNull();

        publisher.publishDue();
        videoSearchIndex.refresh();

        mockMvc.perform(get("/api/search/videos").param("q", "First Logical Search Video"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) firstId));
        mockMvc.perform(get("/api/search/videos").param("q", "Second Logical Search Video"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value((int) secondId));
    }

    private long legacyUpload(String token, String title, String description) throws Exception {
        var request = multipart("/api/videos")
                .file(new MockMultipartFile("file", "demo.mp4", "video/mp4", fixture(2048, 3)))
                .param("title", title)
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (description != null) {
            request.param("description", description);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long completeResumable(String token, byte[] file, String hash, String title, String description) throws Exception {
        JsonNode init = init(token, file.length, hash);
        java.util.UUID uploadId = java.util.UUID.fromString(init.get("uploadId").asString());
        int totalChunks = init.get("totalChunks").asInt();
        for (int i = 0; i < totalChunks; i++) {
            int from = i * CHUNK_SIZE;
            int to = Math.min(file.length, from + CHUNK_SIZE);
            mockMvc.perform(put("/api/uploads/" + uploadId + "/chunks/" + i)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(java.util.Arrays.copyOfRange(file, from, to))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNoContent());
        }
        return complete(token, uploadId.toString(), title, description);
    }

    private JsonNode init(String token, long size, String hash) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "demo.mp4",
                                  "contentType": "video/mp4",
                                  "fileSizeBytes": %d,
                                  "chunkSizeBytes": %d,
                                  "fileSha256": "%s"
                                }
                                """.formatted(size, CHUNK_SIZE, hash))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long complete(String token, String uploadId, String title, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "%s"
                                }
                                """.formatted(title, description))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("video").get("id").asLong();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "StrongPassword123",
                                  "displayName": "%s"
                                }
                                """.formatted(username, username, username)))
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

    private static byte[] fixture(int size, int seed) {
        byte[] bytes = new byte[size];
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) ((i + seed) & 0xFF);
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
