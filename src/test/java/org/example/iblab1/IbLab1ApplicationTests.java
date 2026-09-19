package org.example.iblab1;

import org.example.iblab1.repository.PostRepository;
import org.example.iblab1.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestTransactionConfig.class)
class IbLab1ApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void contextLoads() {
    }
}
