package com.astropaper.api.auth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:astro_paper_auth_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.bootstrap-admin.enabled=true",
    "app.bootstrap-admin.username=initial-admin",
    "app.bootstrap-admin.email=initial-admin@example.test",
    "app.bootstrap-admin.display-name=Initial Admin",
    "app.bootstrap-admin.password=LocalTestPassword123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresCsrfAndAuthenticationAndEnforcesPermissionWhenManagingAccounts() throws Exception {
        CsrfSession adminCsrf = getCsrf(null);

        mockMvc.perform(get("/api/v1/auth/me").session(adminCsrf.session()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Unauthorized"));

        mockMvc.perform(post("/api/v1/auth/login")
                .session(adminCsrf.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"initial-admin\",\"password\":\"LocalTestPassword123!\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/login")
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"initial-admin\",\"password\":\"LocalTestPassword123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("user:manage")));

        CsrfSession authenticatedAdminCsrf = getCsrf(adminCsrf.session());
        mockMvc.perform(get("/api/v1/auth/me").session(authenticatedAdminCsrf.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("initial-admin"));

        MvcResult createdUser = mockMvc.perform(post("/api/v1/admin/users")
                .session(authenticatedAdminCsrf.session())
                .header(CSRF_HEADER, authenticatedAdminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader-one\",\"email\":\"reader@example.test\",\"password\":\"ReaderTestPassword123!\",\"displayName\":\"Reader One\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roles[0]").value("USER"))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems("comment:create", "message:create")))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("user:manage"))))
            .andReturn();
        long userId = extractId(createdUser);

        mockMvc.perform(get("/api/v1/admin/users").session(authenticatedAdminCsrf.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].username").value("initial-admin"))
            .andExpect(jsonPath("$.content[1].username").value("reader-one"));

        mockMvc.perform(post("/api/v1/admin/users")
                .session(authenticatedAdminCsrf.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader-no-csrf\",\"email\":\"reader-no-csrf@example.test\",\"password\":\"ReaderTestPassword000!\",\"displayName\":\"Reader No CSRF\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/roles/USER/permissions")
                .session(authenticatedAdminCsrf.session())
                .header(CSRF_HEADER, authenticatedAdminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionCodes\":[\"comment:create\",\"message:create\",\"user:manage\"]}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/roles/ADMIN/permissions")
                .session(authenticatedAdminCsrf.session())
                .header(CSRF_HEADER, authenticatedAdminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionCodes\":[]}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/users/1/roles")
                .session(authenticatedAdminCsrf.session())
                .header(CSRF_HEADER, authenticatedAdminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"USER\"]}"))
            .andExpect(status().isConflict());

        CsrfSession userCsrf = getCsrf(null);
        mockMvc.perform(post("/api/v1/auth/login")
                .session(userCsrf.session())
                .header(CSRF_HEADER, userCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader-one\",\"password\":\"ReaderTestPassword123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("USER"))
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems("comment:create", "message:create")));

        CsrfSession authenticatedUserCsrf = getCsrf(userCsrf.session());
        mockMvc.perform(post("/api/v1/admin/users")
                .session(authenticatedUserCsrf.session())
                .header(CSRF_HEADER, authenticatedUserCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader-two\",\"email\":\"reader2@example.test\",\"password\":\"ReaderTestPassword456!\",\"displayName\":\"Reader Two\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/roles/USER/permissions")
                .session(authenticatedAdminCsrf.session())
                .header(CSRF_HEADER, authenticatedAdminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionCodes\":[\"message:create\"]}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(authenticatedUserCsrf.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.contains("message:create")));

        mockMvc.perform(get("/api/v1/admin/roles").session(authenticatedUserCsrf.session()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").session(authenticatedUserCsrf.session()))
            .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/users/" + userId + "/status")
                .session(authenticatedAdminCsrf.session())
                .header(CSRF_HEADER, authenticatedAdminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/auth/me").session(authenticatedUserCsrf.session()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users")
                .session(authenticatedUserCsrf.session())
                .header(CSRF_HEADER, authenticatedUserCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reader-three\",\"email\":\"reader3@example.test\",\"password\":\"ReaderTestPassword789!\",\"displayName\":\"Reader Three\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                .session(authenticatedUserCsrf.session())
                .header(CSRF_HEADER, authenticatedUserCsrf.token()))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(userId > 1);
    }

    private CsrfSession getCsrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
        Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("CSRF response did not contain a token.");
        HttpSession csrfSession = result.getRequest().getSession(false);
        if (!(csrfSession instanceof MockHttpSession mockSession)) throw new AssertionError("CSRF request did not create a session.");
        return new CsrfSession(mockSession, matcher.group(1));
    }

    private long extractId(MvcResult result) throws Exception {
        Matcher matcher = ID_PATTERN.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) throw new AssertionError("Account response did not contain an ID.");
        return Long.parseLong(matcher.group(1));
    }

    private record CsrfSession(MockHttpSession session, String token) {
    }
}
