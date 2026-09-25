package com.astropaper.api.articles;

import com.astropaper.api.domain.repository.AuditLogRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
    "spring.datasource.url=jdbc:h2:mem:astro_paper_article_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.bootstrap-admin.enabled=true",
    "app.bootstrap-admin.username=article-admin",
    "app.bootstrap-admin.email=article-admin@example.test",
    "app.bootstrap-admin.display-name=Article Admin",
    "app.bootstrap-admin.password=ArticleTestPassword123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleManagementControllerTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void managesMarkdownPostsWithPermissionAndOwnershipChecks() throws Exception {
        mockMvc.perform(get("/api/v1/admin/posts"))
            .andExpect(status().isUnauthorized());

        CsrfSession admin = login("article-admin", "ArticleTestPassword123!");
        String firstPost = """
            {"slug":"management-admin-post","title":"Admin draft","description":"Admin post description","contentMarkdown":"# Admin body","coverImage":null,"authorName":"Article Admin","timezone":"Asia/Shanghai","featured":false,"canonicalURL":null,"ogImage":null,"hideEditPost":false,"tags":["Backend","Java"]}
            """;
        MvcResult createdAdminPost = mockMvc.perform(post("/api/v1/admin/posts")
                .session(admin.session())
                .header(CSRF_HEADER, admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstPost))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andReturn();
        long adminPostId = extractId(createdAdminPost);

        CsrfSession adminCsrf = getCsrf(admin.session());
        String updatePost = """
            {"slug":"management-admin-post","title":"Admin revised","description":"Updated description","contentMarkdown":"## Updated Markdown","coverImage":"/legacy-assets/cover.jpg","authorName":"Article Admin","timezone":"Asia/Shanghai","featured":true,"canonicalURL":null,"ogImage":null,"hideEditPost":true,"tags":["Backend","Spring Boot"]}
            """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/" + adminPostId)
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePost))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Admin revised"))
            .andExpect(jsonPath("$.contentMarkdown").value("## Updated Markdown"))
            .andExpect(jsonPath("$.featured").value(true))
            .andExpect(jsonPath("$.hideEditPost").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/" + adminPostId + "/status")
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/posts/management-admin-post"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Admin revised"))
            .andExpect(jsonPath("$.contentMarkdown").value("## Updated Markdown"))
            .andExpect(jsonPath("$.tags").value(org.hamcrest.Matchers.hasItem("Spring Boot")));

        MvcResult createdEditor = mockMvc.perform(post("/api/v1/admin/users")
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"article-editor\",\"email\":\"article-editor@example.test\",\"password\":\"EditorTestPassword123!\",\"displayName\":\"Article Editor\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long editorId = extractId(createdEditor);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/users/" + editorId + "/roles")
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"EDITOR\"]}"))
            .andExpect(status().isOk());

        CsrfSession editor = login("article-editor", "EditorTestPassword123!");
        CsrfSession editorCsrf = getCsrf(editor.session());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/" + adminPostId)
                .session(editorCsrf.session())
                .header(CSRF_HEADER, editorCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePost.replace("management-admin-post", "attempted-change")))
            .andExpect(status().isForbidden());

        String editorPost = """
            {"slug":"management-editor-post","title":"Editor draft","description":"Editor post description","contentMarkdown":"Editor Markdown","tags":["Backend"]}
            """;
        MvcResult createdEditorPost = mockMvc.perform(post("/api/v1/admin/posts")
                .session(editorCsrf.session())
                .header(CSRF_HEADER, editorCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editorPost))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorId").value(editorId))
            .andReturn();
        long editorPostId = extractId(createdEditorPost);

        mockMvc.perform(get("/api/v1/admin/posts").session(editorCsrf.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("management-editor-post"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/" + editorPostId + "/status")
                .session(editorCsrf.session())
                .header(CSRF_HEADER, editorCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/posts").session(adminCsrf.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2));

        MvcResult createdUser = mockMvc.perform(post("/api/v1/admin/users")
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"article-reader\",\"email\":\"article-reader@example.test\",\"password\":\"ReaderTestPassword123!\",\"displayName\":\"Article Reader\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        CsrfSession reader = login("article-reader", "ReaderTestPassword123!");
        mockMvc.perform(get("/api/v1/admin/posts").session(reader.session()))
            .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/roles/EDITOR/permissions")
                .session(adminCsrf.session())
                .header(CSRF_HEADER, adminCsrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionCodes\":[\"post:read\",\"post:create\",\"post:update\",\"post:publish\",\"comment:moderate\"]}"))
            .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(
            3,
            auditLogRepository.countByActionIn(java.util.List.of("USER_CREATED", "USER_ROLES_CHANGED"))
        );
        org.junit.jupiter.api.Assertions.assertEquals(
            1,
            auditLogRepository.countByActionIn(java.util.List.of("ROLE_PERMISSIONS_CHANGED"))
        );
    }

    private CsrfSession login(String username, String password) throws Exception {
        CsrfSession csrf = getCsrf(null);
        mockMvc.perform(post("/api/v1/auth/login")
                .session(csrf.session())
                .header(CSRF_HEADER, csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk());
        return getCsrf(csrf.session());
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
        if (!matcher.find()) throw new AssertionError("Response did not contain an ID.");
        return Long.parseLong(matcher.group(1));
    }

    private record CsrfSession(MockHttpSession session, String token) {
    }
}
