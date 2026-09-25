package com.astropaper.api.interactions;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:astro_paper_interaction_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.bootstrap-admin.enabled=true",
    "app.bootstrap-admin.username=interaction-admin",
    "app.bootstrap-admin.email=interaction-admin@example.test",
    "app.bootstrap-admin.display-name=Interaction Admin",
    "app.bootstrap-admin.password=InteractionTestPassword123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractionControllerTest {

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private static final String POST_SLUG = "notes/dynamic-data";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requiresLoginForSubmissionsAndEnforcesModerationPermissions() throws Exception {
        long adminId = jdbcTemplate.queryForObject(
            "select id from users where username = ?",
            Long.class,
            "interaction-admin"
        );
        jdbcTemplate.update("""
            insert into posts (slug, title, description, content_markdown, status, author_id, published_at)
            values (?, 'Dynamic data', 'A test article', 'Body', 'PUBLISHED', ?, current_timestamp)
            """, POST_SLUG, adminId);
        jdbcTemplate.update("""
            insert into posts (slug, title, description, content_markdown, status, author_id, published_at)
            values ('notes/other-article', 'Other article', 'A second test article', 'Body', 'PUBLISHED', ?, current_timestamp)
            """, adminId);

        mockMvc.perform(get("/api/v1/comments").param("postSlug", POST_SLUG))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());

        CsrfSession anonymous = getCsrf(null);
        mockMvc.perform(post("/api/v1/comments")
                .session(anonymous.session())
                .header(CSRF_HEADER, anonymous.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postSlug\":\"" + POST_SLUG + "\",\"body\":\"Anonymous comment\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/messages")
                .session(anonymous.session())
                .header(CSRF_HEADER, anonymous.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Hello\",\"body\":\"Anonymous message\"}"))
            .andExpect(status().isUnauthorized());

        CsrfSession admin = login("interaction-admin", "InteractionTestPassword123!", null);
        MvcResult userResult = mockMvc.perform(post("/api/v1/admin/users")
                .session(admin.session())
                .header(CSRF_HEADER, admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"commenter\",\"email\":\"commenter@example.test\",\"password\":\"CommentTestPassword123!\",\"displayName\":\"Commenter One\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long userId = extractId(userResult);
        CsrfSession user = login("commenter", "CommentTestPassword123!", null);

        MvcResult commentResult = mockMvc.perform(post("/api/v1/comments")
                .session(user.session())
                .header(CSRF_HEADER, user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postSlug\":\"" + POST_SLUG + "\",\"body\":\"A moderated comment\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
        long commentId = extractId(commentResult);

        mockMvc.perform(post("/api/v1/comments")
                .session(user.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postSlug\":\"" + POST_SLUG + "\",\"body\":\"Missing CSRF\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/comments").session(user.session()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/messages").session(user.session()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/comments").param("postSlug", POST_SLUG))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/v1/admin/comments")
                .session(admin.session())
                .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(commentId))
            .andExpect(jsonPath("$.content[0].username").value("commenter"));

        CsrfSession adminWrite = getCsrf(admin.session());
        mockMvc.perform(put("/api/v1/admin/comments/{id}/status", commentId)
                .session(adminWrite.session())
                .header(CSRF_HEADER, adminWrite.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/comments").param("postSlug", POST_SLUG))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].authorName").value("Commenter One"))
            .andExpect(jsonPath("$.content[0].email").doesNotExist());

        CsrfSession userWrite = getCsrf(user.session());
        mockMvc.perform(post("/api/v1/comments")
                .session(userWrite.session())
                .header(CSRF_HEADER, userWrite.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postSlug\":\"notes/other-article\",\"body\":\"Wrong thread\",\"parentId\":" + commentId + "}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/comments")
                .session(userWrite.session())
                .header(CSRF_HEADER, userWrite.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postSlug\":\"" + POST_SLUG + "\",\"body\":\"A reply\",\"parentId\":" + commentId + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/messages")
                .session(userWrite.session())
                .header(CSRF_HEADER, userWrite.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"A private note\",\"body\":\"Please contact me\",\"userId\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("NEW"));

        mockMvc.perform(get("/api/v1/admin/messages")
                .session(admin.session())
                .param("status", "NEW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].userId").value(userId))
            .andExpect(jsonPath("$.content[0].senderName").value("Commenter One"))
            .andExpect(jsonPath("$.content[0].senderEmail").value("commenter@example.test"));

        CsrfSession adminFinalWrite = getCsrf(admin.session());
        mockMvc.perform(put("/api/v1/admin/messages/1/status")
                .session(adminFinalWrite.session())
                .header(CSRF_HEADER, adminFinalWrite.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    private CsrfSession login(String username, String password, MockHttpSession session) throws Exception {
        CsrfSession beforeLogin = getCsrf(session);
        mockMvc.perform(post("/api/v1/auth/login")
                .session(beforeLogin.session())
                .header(CSRF_HEADER, beforeLogin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk());
        return getCsrf(beforeLogin.session());
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
        if (!matcher.find()) throw new AssertionError("The response did not contain an ID.");
        return Long.parseLong(matcher.group(1));
    }

    private record CsrfSession(MockHttpSession session, String token) {
    }
}
