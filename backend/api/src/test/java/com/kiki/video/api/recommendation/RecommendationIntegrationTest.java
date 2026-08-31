package com.kiki.video.api.recommendation;

import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] FIXTURE = fixtureVideo();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void anonymousRequestIsRejectedAndDiscoveryStillWorks() throws Exception {
        mockMvc.perform(get("/api/recommendations/videos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/videos/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/videos/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void newUserGetsColdStartFallback() throws Exception {
        upload(registerAndLogin(unique("catalog")), "Catalog filler");
        String token = registerAndLogin(unique("coldstart"));

        JsonNode body = read(recommend(token, 0, 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coldStart").value(true))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andReturn());
        assertThat(body.get("items").size()).isGreaterThan(0);
        assertThat(body.get("items").get(0).get("recommendationReason").asString())
                .isIn(RecommendationReason.TRENDING, RecommendationReason.RECENT);
    }

    @Test
    void followedCreatorRanksAboveUnrelatedPeer() throws Exception {
        Session creatorA = register(unique("followA"));
        Session creatorB = register(unique("followB"));
        long videoA = upload(creatorA.token, "Followed creator clip");
        long videoB = upload(creatorB.token, "Unrelated creator clip");
        jdbcTemplate.update("UPDATE videos SET view_count = 5 WHERE id IN (?, ?)", videoA, videoB);

        Session viewer = register(unique("follower"));
        follow(viewer.token, creatorA.userId);
        flushRecommendations(viewer.userId);

        JsonNode items = read(recommend(viewer.token, 0, 50).andExpect(status().isOk()).andReturn()).get("items");
        assertThat(indexOf(items, videoA)).isLessThan(indexOf(items, videoB));
        assertThat(card(items, videoA).get("recommendationReason").asString())
                .isEqualTo(RecommendationReason.FOLLOWED);
    }

    @Test
    void repeatedCreatorInteractionsIncreaseAffinity() throws Exception {
        Session creatorA = register(unique("affA"));
        Session creatorB = register(unique("affB"));
        long seenA1 = upload(creatorA.token, "Affinity seen 1");
        long seenA2 = upload(creatorA.token, "Affinity seen 2");
        long unseenA = upload(creatorA.token, "Affinity unseen");
        long unrelated = upload(creatorB.token, "No affinity");
        jdbcTemplate.update("UPDATE videos SET view_count = 8 WHERE id IN (?, ?)", unseenA, unrelated);

        Session viewer = register(unique("affviewer"));
        like(viewer.token, seenA1);
        favorite(viewer.token, seenA1);
        comment(viewer.token, seenA2);
        qualifyAuth(viewer.token, seenA2);
        flushRecommendations(viewer.userId);

        JsonNode items = read(recommend(viewer.token, 0, 50).andExpect(status().isOk()).andReturn()).get("items");
        assertThat(indexOf(items, unseenA)).isLessThan(indexOf(items, unrelated));
        assertThat(card(items, unseenA).get("recommendationReason").asString())
                .isIn(RecommendationReason.ENGAGED_NEW, RecommendationReason.ENGAGED);
    }

    @Test
    void ownVideosAreExcluded() throws Exception {
        Session viewer = register(unique("ownviewer"));
        long own = upload(viewer.token, "My own upload");
        upload(registerAndLogin(unique("otherown")), "Someone else");
        flushRecommendations(viewer.userId);

        JsonNode items = read(recommend(viewer.token, 0, 50).andExpect(status().isOk()).andReturn()).get("items");
        assertThat(contains(items, own)).isFalse();
    }

    @Test
    void duplicateCandidateSourcesDedupeByVideoId() throws Exception {
        Session creator = register(unique("dedupecreator"));
        long video = upload(creator.token, "Dedupe target");
        jdbcTemplate.update("UPDATE videos SET view_count = 50 WHERE id = ?", video);
        Session viewer = register(unique("dedupeviewer"));
        follow(viewer.token, creator.userId);
        flushRecommendations(viewer.userId);

        JsonNode items = read(recommend(viewer.token, 0, 50).andExpect(status().isOk()).andReturn()).get("items");
        int matches = 0;
        for (JsonNode item : items) {
            if (item.get("id").asLong() == video) {
                matches++;
            }
        }
        assertThat(matches).isEqualTo(1);
    }

    @Test
    void qualifiedViewAppliesSeenPenaltyAndHeavySeenCanBeExcluded() throws Exception {
        Session creator = register(unique("seenowner"));
        long unseen = upload(creator.token, "Unseen twin");
        long once = upload(creator.token, "Seen once");
        long heavy = upload(creator.token, "Seen heavily");
        jdbcTemplate.update("UPDATE videos SET view_count = 1000000 WHERE id IN (?, ?, ?)", unseen, once, heavy);

        Session filler = register(unique("seenfiller"));
        for (int i = 0; i < 5; i++) {
            upload(filler.token, "Seen filler " + i);
        }

        Session viewer = register(unique("seenviewer"));
        follow(viewer.token, creator.userId);
        qualifyAuth(viewer.token, once);
        qualifyAuth(viewer.token, heavy);
        flushViewerDedupe(heavy, viewer.userId);
        qualifyAuth(viewer.token, heavy);
        flushViewerDedupe(heavy, viewer.userId);
        qualifyAuth(viewer.token, heavy);
        flushRecommendations(viewer.userId);

        JsonNode items = read(recommend(viewer.token, 0, 3).andExpect(status().isOk()).andReturn()).get("items");
        assertThat(indexOf(items, unseen)).isLessThan(indexOf(items, once));
        assertThat(contains(items, heavy)).isFalse();
        Integer heavyCount = jdbcTemplate.queryForObject(
                "SELECT qualified_view_count FROM user_video_qualified_views WHERE user_id = ? AND video_id = ?",
                Integer.class,
                viewer.userId,
                heavy
        );
        assertThat(heavyCount).isEqualTo(3);
    }

    @Test
    void rankingIsDeterministicAndPaginationIsBounded() throws Exception {
        Session creator = register(unique("pageowner"));
        for (int i = 0; i < 6; i++) {
            long id = upload(creator.token, "Page clip " + i);
            jdbcTemplate.update("UPDATE videos SET view_count = ? WHERE id = ?", 10 + i, id);
        }
        Session viewer = register(unique("pageviewer"));
        follow(viewer.token, creator.userId);
        flushRecommendations(viewer.userId);

        JsonNode first = read(recommend(viewer.token, 0, 2)
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andReturn());
        JsonNode again = read(recommend(viewer.token, 0, 2).andReturn());
        JsonNode second = read(recommend(viewer.token, 1, 2).andReturn());
        JsonNode oversized = read(recommend(viewer.token, 0, 999)
                .andExpect(jsonPath("$.size").value(50))
                .andReturn());

        assertThat(first.get("items").toString()).isEqualTo(again.get("items").toString());
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(second.get("items").size()).isEqualTo(2);
        assertThat(first.get("items").get(0).get("id").asLong())
                .isNotEqualTo(second.get("items").get(0).get("id").asLong());
        assertThat(oversized.get("size").asInt()).isEqualTo(50);
    }

    @Test
    void redisCacheHitReturnsStalePageUntilFlushed() throws Exception {
        Session creatorA = register(unique("cacheA"));
        Session creatorB = register(unique("cacheB"));
        long videoA = upload(creatorA.token, "Cache A");
        long videoB = upload(creatorB.token, "Cache B");
        jdbcTemplate.update("UPDATE videos SET view_count = 3 WHERE id IN (?, ?)", videoA, videoB);

        Session viewer = register(unique("cacheviewer"));
        follow(viewer.token, creatorA.userId);
        flushRecommendations(viewer.userId);

        JsonNode first = read(recommend(viewer.token, 0, 20).andReturn()).get("items");
        assertThat(indexOf(first, videoA)).isLessThan(indexOf(first, videoB));

        follow(viewer.token, creatorB.userId);
        jdbcTemplate.update("UPDATE videos SET view_count = 80 WHERE id = ?", videoB);
        JsonNode cached = read(recommend(viewer.token, 0, 20).andReturn()).get("items");
        assertThat(cached.toString()).isEqualTo(first.toString());

        flushRecommendations(viewer.userId);
        JsonNode fresh = read(recommend(viewer.token, 0, 20).andReturn()).get("items");
        assertThat(indexOf(fresh, videoB)).isLessThan(indexOf(fresh, videoA));
    }

    @Test
    void qualifiedViewUpsertIsUniquePerUserAndVideo() throws Exception {
        Session owner = register(unique("uniqowner"));
        long video = upload(owner.token, "Uniq view");
        Session viewer = register(unique("uniqviewer"));
        qualifyAuth(viewer.token, video);
        flushViewerDedupe(video, viewer.userId);
        qualifyAuth(viewer.token, video);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_video_qualified_views WHERE user_id = ? AND video_id = ?",
                Integer.class,
                viewer.userId,
                video
        );
        Integer count = jdbcTemplate.queryForObject(
                "SELECT qualified_view_count FROM user_video_qualified_views WHERE user_id = ? AND video_id = ?",
                Integer.class,
                viewer.userId,
                video
        );
        assertThat(rows).isEqualTo(1);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void logicalVideosSharingMediaRemainIndependent() throws Exception {
        Session creatorA = register(unique("shareA"));
        Session creatorB = register(unique("shareB"));
        long videoA = upload(creatorA.token, "Shared media rec A");
        long videoB = upload(creatorB.token, "Shared media rec B");
        Long mediaA = jdbcTemplate.queryForObject("SELECT media_object_id FROM videos WHERE id = ?", Long.class, videoA);
        Long mediaB = jdbcTemplate.queryForObject("SELECT media_object_id FROM videos WHERE id = ?", Long.class, videoB);
        assertThat(mediaA).isEqualTo(mediaB);

        Session viewer = register(unique("shareviewer"));
        follow(viewer.token, creatorA.userId);
        follow(viewer.token, creatorB.userId);
        qualifyAuth(viewer.token, videoA);
        flushRecommendations(viewer.userId);

        JsonNode items = read(recommend(viewer.token, 0, 50).andReturn()).get("items");
        assertThat(contains(items, videoA)).isTrue();
        assertThat(contains(items, videoB)).isTrue();
        assertThat(indexOf(items, videoB)).isLessThan(indexOf(items, videoA));
    }

    @Test
    void flywayCreatedQualifiedViewTableAndIndexes() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'user_video_qualified_views'",
                Integer.class
        );
        Integer pk = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename = 'user_video_qualified_views'
                  AND indexname = 'user_video_qualified_views_pkey'
                """,
                Integer.class
        );
        assertThat(tables).isEqualTo(1);
        assertThat(pk).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions recommend(String token, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/recommendations/videos")
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private void follow(String token, long userId) throws Exception {
        mockMvc.perform(put("/api/users/" + userId + "/follow").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void like(String token, long videoId) throws Exception {
        mockMvc.perform(put("/api/videos/" + videoId + "/like").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void favorite(String token, long videoId) throws Exception {
        mockMvc.perform(put("/api/videos/" + videoId + "/favorite").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void comment(String token, long videoId) throws Exception {
        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"affinity note\"}"))
                .andExpect(status().isCreated());
    }

    private void qualifyAuth(String token, long videoId) throws Exception {
        mockMvc.perform(post("/api/videos/" + videoId + "/views/qualify")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"watchedMs":10000,"clientViewId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counted").value(true));
    }

    private void flushViewerDedupe(long videoId, long userId) {
        redis.delete("kiki:video:" + videoId + ":view-dedupe:u:" + userId);
    }

    private void flushRecommendations(long userId) {
        Set<String> keys = redis.keys("kiki:recommendations:user:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static int indexOf(JsonNode items, long videoId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).get("id").asLong() == videoId) {
                return i;
            }
        }
        throw new AssertionError("video " + videoId + " missing from recommendations");
    }

    private static boolean contains(JsonNode items, long videoId) {
        for (JsonNode item : items) {
            if (item.get("id").asLong() == videoId) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode card(JsonNode items, long videoId) {
        for (JsonNode item : items) {
            if (item.get("id").asLong() == videoId) {
                return item;
            }
        }
        throw new AssertionError("video " + videoId + " missing from recommendations");
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

    private Session register(String username) throws Exception {
        String token = registerAndLogin(username);
        MvcResult me = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        long userId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asLong();
        return new Session(userId, token);
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

    private record Session(long userId, String token) {
    }
}
