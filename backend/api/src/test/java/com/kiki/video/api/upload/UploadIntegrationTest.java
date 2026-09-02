package com.kiki.video.api.upload;

import com.kiki.video.api.support.AbstractIntegrationTest;
import com.kiki.video.api.support.MockMvcStreaming;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.mapper.UploadChunkMapper;
import com.kiki.video.api.upload.mapper.UploadSessionMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.upload.model.UploadSession;
import com.kiki.video.api.upload.service.UploadService;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UploadIntegrationTest extends AbstractIntegrationTest {

    private static final int CHUNK_SIZE = 256 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UploadSessionMapper uploadSessionMapper;

    @Autowired
    private UploadChunkMapper uploadChunkMapper;

    @Autowired
    private MediaObjectMapper mediaObjectMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private MinioClient minioClient;

    @Test
    void unauthenticatedInitIsRejected() throws Exception {
        byte[] file = fixture(1024, 1);
        mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initJson("demo.mp4", file.length, sha256(file))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidSha256IsRejected() throws Exception {
        String token = registerAndLogin(unique("badhash"));
        mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initJson("demo.mp4", 1024, "not-a-hash"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void oversizedFileIsRejected() throws Exception {
        String token = registerAndLogin(unique("huge"));
        mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initJson("demo.mp4", 33L * 1024 * 1024, "ab".repeat(32)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_FILE_TOO_LARGE"));
    }

    @Test
    void initCreatesSessionAndSameUserCanResume() throws Exception {
        String token = registerAndLogin(unique("resume"));
        byte[] file = fixture(600_000, 2);
        String hash = sha256(file);

        JsonNode first = init(token, file.length, hash);
        assertThat(first.get("deduplicated").asBoolean()).isFalse();
        assertThat(first.get("uploadRequired").asBoolean()).isTrue();
        assertThat(first.get("totalChunks").asInt()).isEqualTo(3);
        assertThat(first.get("chunkSizeBytes").asLong()).isEqualTo(CHUNK_SIZE);
        assertThat(first.get("uploadedChunks").size()).isEqualTo(0);

        JsonNode second = init(token, file.length, hash);
        assertThat(second.get("uploadId").asString()).isEqualTo(first.get("uploadId").asString());
    }

    @Test
    void chunkUploadRejectsWrongOwnerOutOfRangeAndWrongSize() throws Exception {
        String ownerToken = registerAndLogin(unique("owner"));
        String otherToken = registerAndLogin(unique("other"));
        byte[] file = fixture(CHUNK_SIZE + 100, 3);
        JsonNode init = init(ownerToken, file.length, sha256(file));
        String uploadId = init.get("uploadId").asString();

        mockMvc.perform(put("/api/uploads/" + uploadId + "/chunks/0")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunk(file, 0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));

        mockMvc.perform(put("/api/uploads/" + uploadId + "/chunks/9")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[10])
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_CHUNK_OUT_OF_RANGE"));

        mockMvc.perform(put("/api/uploads/" + uploadId + "/chunks/0")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[16])
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_CHUNK_SIZE_INVALID"));
    }

    @Test
    void duplicateChunkUploadIsIdempotentAndStatusListsMissingChunks() throws Exception {
        String token = registerAndLogin(unique("chunks"));
        byte[] file = fixture(CHUNK_SIZE + 50, 4);
        JsonNode init = init(token, file.length, sha256(file));
        String uploadId = init.get("uploadId").asString();

        putChunk(token, uploadId, 0, chunk(file, 0));
        putChunk(token, uploadId, 0, chunk(file, 0));

        mockMvc.perform(get("/api/uploads/" + uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedChunks.length()").value(1))
                .andExpect(jsonPath("$.uploadedChunks[0]").value(0))
                .andExpect(jsonPath("$.missingChunks[0]").value(1));
    }

    @Test
    void completeRejectsMissingChunksThenSucceedsAndIsIdempotent() throws Exception {
        String token = registerAndLogin(unique("complete"));
        byte[] file = fixture(600_000, 5);
        String hash = sha256(file);
        JsonNode init = init(token, file.length, hash);
        String uploadId = init.get("uploadId").asString();

        putChunk(token, uploadId, 0, chunk(file, 0));
        mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Incomplete","description":"Nope"}
                                """)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_INCOMPLETE"));

        putChunk(token, uploadId, 1, chunk(file, 1));
        putChunk(token, uploadId, 2, chunk(file, 2));

        MvcResult created = mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Chunked video","description":"From chunks"}
                                """)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.title").value("Chunked video"))
                .andExpect(jsonPath("$.video.fileSizeBytes").value(file.length))
                .andExpect(jsonPath("$.deduplicated").value(false))
                .andReturn();

        long videoId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/video/id").asLong();
        Video video = videoMapper.findById(videoId);
        assertThat(video.getFileSha256()).isEqualTo(hash);
        assertThat(video.getObjectKey()).isEqualTo("raw/" + hash);
        assertThat(video.getFileSizeBytes()).isEqualTo(file.length);
        assertThat(minioClient.statObject(StatObjectArgs.builder()
                .bucket("videos")
                .object(video.getObjectKey())
                .build()).size()).isEqualTo(file.length);
        assertThat(listKeys(UploadObjectKeys.chunkPrefix(UUID.fromString(uploadId)))).isEmpty();

        try (var stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("videos")
                .object(video.getObjectKey())
                .build())) {
            assertThat(stream.readAllBytes()).isEqualTo(file);
        }
        MockMvcStreaming.awaitStreamingResponse(mockMvc, get("/api/videos/" + videoId + "/content")
                        .header(HttpHeaders.RANGE, "bytes=0-15"))
                .andExpect(status().isPartialContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(java.util.Arrays.copyOfRange(file, 0, 16)));

        MvcResult replay = mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Chunked video","description":"From chunks"}
                                """)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).at("/video/id").asLong())
                .isEqualTo(videoId);
    }

    @Test
    void hashMismatchFailsComplete() throws Exception {
        String token = registerAndLogin(unique("mismatch"));
        byte[] file = fixture(CHUNK_SIZE, 6);
        String wrongHash = sha256(new byte[] {9, 8, 7, 6});
        JsonNode init = init(token, file.length, wrongHash);
        String uploadId = init.get("uploadId").asString();
        putChunk(token, uploadId, 0, file);

        mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad hash"}
                                """)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_HASH_MISMATCH"));
    }

    @Test
    void dedupeReusesPhysicalObjectAndCreatesSeparateLogicalVideos() throws Exception {
        byte[] file = fixture(CHUNK_SIZE + 12, 7);
        String hash = sha256(file);
        String aliceToken = registerAndLogin(unique("alice"));
        String bobToken = registerAndLogin(unique("bob"));

        JsonNode aliceInit = init(aliceToken, file.length, hash);
        uploadAll(aliceToken, aliceInit.get("uploadId").asString(), file);
        long aliceVideo = complete(aliceToken, aliceInit.get("uploadId").asString(), "Alice copy");

        JsonNode bobInit = init(bobToken, file.length, hash);
        assertThat(bobInit.get("deduplicated").asBoolean()).isTrue();
        assertThat(bobInit.get("uploadRequired").asBoolean()).isFalse();
        long bobVideo = complete(bobToken, bobInit.get("uploadId").asString(), "Bob copy");

        assertThat(aliceVideo).isNotEqualTo(bobVideo);
        Video first = videoMapper.findById(aliceVideo);
        Video second = videoMapper.findById(bobVideo);
        assertThat(first.getMediaObjectId()).isEqualTo(second.getMediaObjectId());
        assertThat(first.getObjectKey()).isEqualTo(second.getObjectKey());
        MediaObject media = mediaObjectMapper.findBySha256(hash);
        assertThat(media).isNotNull();
        assertThat(media.getId()).isEqualTo(first.getMediaObjectId());
        assertThat(listKeys("raw/" + hash)).containsExactly("raw/" + hash);
    }

    @Test
    void expiredSessionsAreMarkedExpiredAndTemporaryChunksDeleted() throws Exception {
        String token = registerAndLogin(unique("expire"));
        byte[] file = fixture(CHUNK_SIZE, 8);
        JsonNode init = init(token, file.length, sha256(file));
        String uploadId = init.get("uploadId").asString();
        putChunk(token, uploadId, 0, file);

        UUID id = UUID.fromString(uploadId);
        uploadSessionMapper.updateExpiresAt(id, Instant.now().minusSeconds(60), Instant.now());
        assertThat(uploadService.cleanupExpiredSessions()).isGreaterThanOrEqualTo(1);

        UploadSession session = uploadSessionMapper.findById(id);
        assertThat(session.getStatus().name()).isEqualTo("EXPIRED");
        assertThat(uploadChunkMapper.findIndexes(id)).isEmpty();
        assertThat(listKeys(UploadObjectKeys.chunkPrefix(id))).isEmpty();

        mockMvc.perform(get("/api/uploads/" + uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("UPLOAD_EXPIRED"));
    }

    private JsonNode init(String token, long fileSize, String sha256) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/uploads/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initJson("demo.mp4", fileSize, sha256))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void putChunk(String token, String uploadId, int index, byte[] bytes) throws Exception {
        mockMvc.perform(put("/api/uploads/" + uploadId + "/chunks/" + index)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(bytes)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    private void uploadAll(String token, String uploadId, byte[] file) throws Exception {
        int total = UploadMath.totalChunks(file.length, CHUNK_SIZE);
        for (int i = 0; i < total; i++) {
            putChunk(token, uploadId, i, chunk(file, i));
        }
    }

    private long complete(String token, String uploadId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/uploads/" + uploadId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/video/id").asLong();
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

    private List<String> listKeys(String prefix) throws Exception {
        List<String> keys = new ArrayList<>();
        for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket("videos")
                .prefix(prefix)
                .recursive(true)
                .build())) {
            keys.add(result.get().objectName());
        }
        return keys;
    }

    private static byte[] chunk(byte[] file, int index) {
        int start = index * CHUNK_SIZE;
        int end = Math.min(start + CHUNK_SIZE, file.length);
        byte[] part = new byte[end - start];
        System.arraycopy(file, start, part, 0, part.length);
        return part;
    }

    private static String initJson(String fileName, long fileSizeBytes, String sha256) {
        return """
                {
                  "fileName": "%s",
                  "fileSizeBytes": %d,
                  "contentType": "video/mp4",
                  "fileSha256": "%s"
                }
                """.formatted(fileName, fileSizeBytes, sha256);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] fixture(int size, int seed) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) seed;
        if (size > 7) {
            bytes[4] = 'f';
            bytes[5] = 't';
            bytes[6] = 'y';
            bytes[7] = 'p';
        }
        for (int i = 8; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 31) + seed);
        }
        return bytes;
    }
}
