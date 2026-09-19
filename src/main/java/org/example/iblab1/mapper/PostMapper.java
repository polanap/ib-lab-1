package org.example.iblab1.mapper;

import lombok.RequiredArgsConstructor;
import org.example.iblab1.model.dto.response.AuthorResponse;
import org.example.iblab1.model.dto.response.PostResponse;
import org.example.iblab1.model.entity.Post;
import org.example.iblab1.model.entity.User;
import org.example.iblab1.security.HtmlSanitizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostMapper {
    private final HtmlSanitizer htmlSanitizer;

    public PostResponse toDto(Post post) {
        if (post == null) return null;

        return PostResponse.builder()
                .id(post.getId())
                .title(htmlSanitizer.escape(post.getTitle()))
                .content(htmlSanitizer.escape(post.getContent()))
                .createdAt(post.getCreatedAt())
                .author(toAuthorDto(post.getAuthor()))
                .build();
    }

    private AuthorResponse toAuthorDto(User user) {
        if (user == null) return null;

        return AuthorResponse.builder()
                .id(user.getId())
                .login(htmlSanitizer.escape(user.getLogin()))
                .build();
    }
}
