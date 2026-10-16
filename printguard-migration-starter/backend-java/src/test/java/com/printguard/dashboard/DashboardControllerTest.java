package com.printguard.dashboard;

import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("testuser").isEmpty()) {
            UserEntity user = new UserEntity();
            user.setUsername("testuser");
            user.setEmail("testuser@printguard.local");
            user.setPassword("pbkdf2_sha256$hashed");
            userRepository.save(user);
        }
    }

    @Test
    void getStatsUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getStatsReturnsExpectedDatabaseMappedFields() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_models", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.ready_models", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.total_prints", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.completed_prints", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.failure_rate", notNullValue()))
                .andExpect(jsonPath("$.recent_errors", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.ai_monitoring_status", is("Active")))
                .andExpect(jsonPath("$.weekly_chart", hasSize(7)))
                .andExpect(jsonPath("$.weekly_chart[0].name", notNullValue()))
                .andExpect(jsonPath("$.weekly_chart[0].date", notNullValue()))
                .andExpect(jsonPath("$.weekly_chart[0].models", notNullValue()))
                .andExpect(jsonPath("$.weekly_chart[0].slicing", notNullValue()))
                .andExpect(jsonPath("$.weekly_chart[0].prints", notNullValue()))
                .andExpect(jsonPath("$.weekly_chart[0].defects", notNullValue()));
    }

    @Test
    void getAnalyticsUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/analytics/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getAnalyticsReturnsWeeklyChartDirectly() throws Exception {
        mockMvc.perform(get("/api/dashboard/analytics/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly_chart", hasSize(7)))
                .andExpect(jsonPath("$.weekly_chart[6].date", notNullValue()))
                .andExpect(jsonPath("$.timezone", notNullValue()));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getPrinterStatusHonestReportingWhenOffline() throws Exception {
        mockMvc.perform(get("/api/dashboard/printer-status/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.printer_connected", is(false)))
                .andExpect(jsonPath("$.simulated", is(true)))
                .andExpect(jsonPath("$.telemetry_source", is("offline_simulated")))
                .andExpect(jsonPath("$.limitation_note", containsString("offline")));
    }
}
