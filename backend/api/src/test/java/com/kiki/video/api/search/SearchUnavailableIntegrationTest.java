package com.kiki.video.api.search;

import com.kiki.video.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchUnavailableIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/search/videos").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }

    @Test
    void missingQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/search/videos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void searchReturnsUnavailableWhenElasticsearchIsDisabled() throws Exception {
        mockMvc.perform(get("/api/search/videos").param("q", "trailer"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"));
    }
}
