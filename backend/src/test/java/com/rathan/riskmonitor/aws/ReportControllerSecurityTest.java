package com.rathan.riskmonitor.aws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ROLE_MANAGER can successfully retrieve ML reports")
    void testManagerCanAccessReports() throws Exception {
        mockMvc.perform(get("/api/reports")
                        .with(httpBasic("manager1", "Manager123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("ROLE_ANALYST is forbidden from retrieving ML reports (403)")
    void testAnalystForbiddenFromReports() throws Exception {
        mockMvc.perform(get("/api/reports")
                        .with(httpBasic("analyst1", "Analyst123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to reports endpoint returns 401")
    void testUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());
    }
}
