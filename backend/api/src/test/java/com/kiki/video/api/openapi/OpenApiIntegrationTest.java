package com.kiki.video.api.openapi;

import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApiDocumentIsPublicAndDescribesProductApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("kiki-video-platform API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme").value("bearer"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String document = root.toString();
        assertThat(document).doesNotContain("/actuator");
        assertThat(document).doesNotContain("minioadmin");
        assertThat(document.toLowerCase()).doesNotContain("jwt_secret");
        assertThat(root.path("paths").has("/api/users/{userId}")).isTrue();
        assertThat(root.path("paths").has("/api/users/{userId}/videos")).isTrue();
        assertThat(root.path("paths").has("/api/videos/recent")).isTrue();
        assertThat(root.path("paths").has("/api/search/videos")).isTrue();
        assertThat(root.path("paths").has("/api/users/me")).isTrue();
        assertThat(root.path("paths").has("/api/notifications")).isTrue();

        JsonNode publicProfile = root.path("paths").path("/api/users/{userId}").path("get");
        assertThat(publicProfile.path("security").isMissingNode() || publicProfile.path("security").isEmpty()).isTrue();
        assertThat(publicProfile.path("tags").toString()).contains("Users");

        JsonNode me = root.path("paths").path("/api/users/me").path("get");
        assertThat(me.path("security").toString()).contains("bearer-jwt");

        JsonNode notifications = root.path("paths").path("/api/notifications").path("get");
        assertThat(notifications.path("security").toString()).contains("bearer-jwt");
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
