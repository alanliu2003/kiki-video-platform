package com.kiki.video.api.video;

import com.kiki.video.api.support.AbstractIntegrationTest;
import com.kiki.video.api.support.MockMvcStreaming;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VideoIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private MinioClient minioClient;

    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/videos")
                        .file(mp4("demo.mp4"))
                        .param("title", "Demo video"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validMp4UploadPersistsMetadataAndMinioObject() throws Exception {
        String username = unique("uploader");
        String token = registerAndLogin(username);

        MvcResult result = mockMvc.perform(multipart("/api/videos")
                        .file(mp4("../../evil.mp4"))
                        .param("title", "  Demo video  ")
                        .param("description", "First upload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Demo video"))
                .andExpect(jsonPath("$.description").value("First upload"))
                .andExpect(jsonPath("$.owner.username").value(username))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.fileSizeBytes").value(FIXTURE.length))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andExpect(jsonPath("$.originalFilename").doesNotExist())
                .andReturn();

        long videoId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Video stored = videoMapper.findById(videoId);
        assertThat(stored).isNotNull();
        assertThat(stored.getOwnerUserId()).isNotNull();
        assertThat(stored.getObjectKey()).startsWith("raw/");
        assertThat(stored.getObjectKey()).doesNotContain("..");
        assertThat(stored.getObjectKey()).doesNotContain("evil");
        assertThat(stored.getMediaObjectId()).isNotNull();
        assertThat(stored.getProcessingStatus().name()).isEqualTo("PENDING");
        assertThat(stored.getOriginalFilename()).isEqualTo("evil.mp4");
        assertThat(stored.getContentType()).isEqualTo("video/mp4");
        assertThat(stored.getFileSizeBytes()).isEqualTo(FIXTURE.length);

        var stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket("videos")
                .object(stored.getObjectKey())
                .build());
        assertThat(stat.size()).isEqualTo(FIXTURE.length);
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        String token = registerAndLogin(unique("empty"));
        mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]))
                        .param("title", "Empty")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIDEO_FILE_REQUIRED"));
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        String token = registerAndLogin(unique("type"));
        mockMvc.perform(multipart("/api/videos")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .param("title", "Notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_VIDEO_TYPE"));
    }

    @Test
    void invalidTitleIsRejected() throws Exception {
        String token = registerAndLogin(unique("title"));
        mockMvc.perform(multipart("/api/videos")
                        .file(mp4("demo.mp4"))
                        .param("title", " ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VIDEO_TITLE"));

        mockMvc.perform(multipart("/api/videos")
                        .file(mp4("demo.mp4"))
                        .param("title", "x".repeat(121))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VIDEO_TITLE"));
    }

    @Test
    void getExistingVideoReturnsPublicMetadata() throws Exception {
        String username = unique("public");
        long videoId = upload(registerAndLogin(username), "Public video");

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(videoId))
                .andExpect(jsonPath("$.title").value("Public video"))
                .andExpect(jsonPath("$.owner.username").value(username))
                .andExpect(jsonPath("$.objectKey").doesNotExist());
    }

    @Test
    void getUnknownVideoReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/videos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
    }

    @Test
    void myVideosRequiresAuthenticationAndReturnsOnlyCurrentUser() throws Exception {
        mockMvc.perform(get("/api/users/me/videos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String alice = unique("alice");
        String bob = unique("bob");
        String aliceToken = registerAndLogin(alice);
        String bobToken = registerAndLogin(bob);
        long first = upload(aliceToken, "Alice older");
        Thread.sleep(20);
        long second = upload(aliceToken, "Alice newer");
        upload(bobToken, "Bob video");

        mockMvc.perform(get("/api/users/me/videos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[0].title").value("Alice newer"))
                .andExpect(jsonPath("$.items[1].id").value(first))
                .andExpect(jsonPath("$.items[1].title").value("Alice older"));
    }

    @Test
    void contentStreamsWithAndWithoutRange() throws Exception {
        long videoId = upload(registerAndLogin(unique("range")), "Range video");

        MockMvcStreaming.awaitStreamingResponse(mockMvc, get("/api/videos/" + videoId + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, FIXTURE.length))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(FIXTURE));

        MockMvcStreaming.awaitStreamingResponse(mockMvc, get("/api/videos/" + videoId + "/content")
                        .header(HttpHeaders.RANGE, "bytes=0-1023"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-1023/" + FIXTURE.length))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 1024))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(java.util.Arrays.copyOfRange(FIXTURE, 0, 1024)));

        mockMvc.perform(get("/api/videos/" + videoId + "/content")
                        .header(HttpHeaders.RANGE, "bytes=abc-def"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));

        mockMvc.perform(get("/api/videos/" + videoId + "/content")
                        .header(HttpHeaders.RANGE, "bytes=999999-1000000"))
                .andExpect(status().isRequestedRangeNotSatisfiable());

        mockMvc.perform(get("/api/videos/999999/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
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
