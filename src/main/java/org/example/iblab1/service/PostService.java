package org.example.iblab1.service;

import lombok.RequiredArgsConstructor;
import org.example.iblab1.exceptions.UnauthorizedException;
import org.example.iblab1.model.dto.request.PostRequest;
import org.example.iblab1.model.entity.Post;
import org.example.iblab1.model.entity.User;
import org.example.iblab1.repository.PostRepository;
import org.example.iblab1.repository.UserRepository;
import org.example.iblab1.security.HtmlSanitizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final HtmlSanitizer htmlSanitizer;

    @Transactional(readOnly = true)
    public Page<Post> findAll(Pageable pageable) {
        return postRepository.findAllBy(pageable);
    }

    public Post createPost(PostRequest postRequest, String login) {
        User author = userRepository.findByLogin(login)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));

        Post post = new Post();
        post.setTitle(htmlSanitizer.sanitize(postRequest.getTitle()));
        post.setContent(htmlSanitizer.sanitize(postRequest.getContent()));
        post.setAuthor(author);

        return postRepository.save(post);
    }
}
