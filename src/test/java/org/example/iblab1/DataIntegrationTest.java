package org.example.iblab1;

import org.example.iblab1.model.dto.request.LoginRequest;
import org.example.iblab1.model.dto.request.PostRequest;
import org.example.iblab1.model.entity.Post;
import org.example.iblab1.model.entity.User;
import org.example.iblab1.repository.PostRepository;
import org.example.iblab1.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTransactionConfig.class)
class DataIntegrationTest {
    private static final String LOGIN = "alice";
    private static final String PASSWORD = "SuperSecret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PostRepository postRepository;

    private User author;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(1);
        author.setLogin(LOGIN);
        author.setPasswordHash(passwordEncoder.encode(PASSWORD));
        author.setIsActive(true);
        author.setRegistrationDate(OffsetDateTime.now());

        when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.of(author));
    }

    private String authenticate() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(LOGIN, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + objectMapper.readTree(body).get("token").asString();
    }

    private Post postEntity(Integer id, String title, String content) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(title);
        post.setContent(content);
        post.setCreatedAt(OffsetDateTime.now());
        post.setAuthor(author);
        return post;
    }

    @Test
    void getDataRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/data"))
                .andExpect(status().isUnauthorized());

        verify(postRepository, never()).findAllBy(any());
    }

    @Test
    void createDataRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostRequest("Title", "Content"))))
                .andExpect(status().isUnauthorized());

        verify(postRepository, never()).save(any());
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String forged = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJhbGljZSIsImV4cCI6NDg4NTAwMDAwMH0"
                + ".ZmFrZS1zaWduYXR1cmUtdGhhdC1kb2VzLW5vdC12ZXJpZnk";

        mockMvc.perform(get("/api/data").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDataReturnsPostsForAuthenticatedUser() throws Exception {
        Page<Post> page = new PageImpl<>(List.of(postEntity(1, "First post", "Hello world")));
        when(postRepository.findAllBy(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/data").header("Authorization", authenticate()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First post"))
                .andExpect(jsonPath("$[0].content").value("Hello world"))
                .andExpect(jsonPath("$[0].author.login").value(LOGIN));
    }

    @Test
    void createDataStoresPostForAuthenticatedUser() throws Exception {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(42);
            saved.setCreatedAt(OffsetDateTime.now());
            return saved;
        });

        mockMvc.perform(post("/api/data")
                        .header("Authorization", authenticate())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostRequest("Title", "Content"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Title"))
                .andExpect(jsonPath("$.author.login").value(LOGIN));
    }

    @Test
    void createDataRejectsEmptyPayload() throws Exception {
        mockMvc.perform(post("/api/data")
                        .header("Authorization", authenticate())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostRequest("", ""))))
                .andExpect(status().isBadRequest());

        verify(postRepository, never()).save(any());
    }

    /**
     * Input consisting only of markup becomes empty after sanitizing, which the
     * database rejects — the request must be refused with 400 instead of failing.
     */
    @Test
    void createDataRejectsMarkupOnlyPayload() throws Exception {
        mockMvc.perform(post("/api/data")
                        .header("Authorization", authenticate())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostRequest(
                                "<script>alert('xss')</script>",
                                "<b>content</b>"))))
                .andExpect(status().isBadRequest());

        verify(postRepository, never()).save(any());
    }

    /**
     * Markup is stripped when a post is stored, so no script tag can be persisted.
     */
    @Test
    void createDataStripsHtmlFromUserInput() throws Exception {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(7);
            saved.setCreatedAt(OffsetDateTime.now());
            return saved;
        });

        mockMvc.perform(post("/api/data")
                        .header("Authorization", authenticate())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostRequest(
                                "<script>alert('xss')</script>Safe title",
                                "<img src=x onerror=alert(1)>payload"))))
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(jsonPath("$.title").value("Safe title"))
                .andExpect(jsonPath("$.content").value("payload"));
    }

    /**
     * Anything that survived in the database (for example data written before the
     * sanitizer existed) is HTML-escaped on its way out.
     */
    @Test
    void getDataEscapesStoredMarkup() throws Exception {
        Page<Post> page = new PageImpl<>(List.of(
                postEntity(2, "<script>alert('xss')</script>", "<b>bold</b> & dangerous")));
        when(postRepository.findAllBy(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/data").header("Authorization", authenticate()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(jsonPath("$[0].title").value("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"))
                .andExpect(jsonPath("$[0].content").value("&lt;b&gt;bold&lt;/b&gt; &amp; dangerous"));
    }
}
