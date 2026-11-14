package com.example.taskapp;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class TaskAuthIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationAuthenticationProductAccessAndLogoutWorkEndToEnd() throws Exception {
        String standardToken = registerAndLogin(
                "northstar1", "north_star@example.com", "Standard User");

        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(standardToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Welcome northstar1")));
        mockMvc.perform(get("/api/settings").header("Authorization", bearer(standardToken)))
                .andExpect(status().isForbidden());

        String product = """
                {"name":"Field Notebook","description":"160 pages","price":12.50,"stock":35}
                """;
        MvcResult created = mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(standardToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Field Notebook")))
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/products").header("Authorization", bearer(standardToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.page.totalPages").value(1));
        mockMvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", bearer(standardToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Notebook","description":"Updated","price":15.75,"stock":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(15.75));
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", bearer(standardToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Notebook"))
                .andExpect(jsonPath("$.stock").value(4));
        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", bearer(standardToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", bearer(standardToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer(standardToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("northstar1 Logged Out")));
        mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(standardToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectAnonymousAndMalformedTokens() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void invalidProductValuesAreRejectedBeforeDatabaseWrites() throws Exception {
        String token = registerAndLogin(
                "validator01", "validation_user@example.com", "Standard User");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalid product","price":1.234,"stock":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administratorCanAccessSettingsAndInvalidLoginUsesRequiredMessage() throws Exception {
        String administratorToken = registerAndLogin(
                "adminuser01", "admin_user@example.com", "Administrator");

        mockMvc.perform(get("/api/settings").header("Authorization", bearer(administratorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Administrator settings")));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin_user@example.com","password":"Wrong!123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Login Failed")));
    }

    @Test
    void fiveIncorrectOtpAttemptsInvalidateTheChallenge() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"otpuser001","email":"otp_user@example.com",
                                 "password":"Strong!123","userType":"Standard User"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isAccepted())
                .andReturn();
        String correctOtp = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("developmentOtp").asText();
        String incorrectOtp = correctOtp.equals("000000") ? "000001" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"otp_user@example.com","otp":"%s"}
                                    """.formatted(incorrectOtp)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"otp_user@example.com","otp":"%s"}
                                """.formatted(correctOtp)))
                .andExpect(status().isBadRequest());
    }

    private String registerAndLogin(String username, String email, String userType) throws Exception {
        String registration = """
                {"username":"%s","email":"%s","password":"Strong!123","userType":"%s"}
                """.formatted(username, email, userType);
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode otp = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("developmentOtp");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp.asText())))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Strong!123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
