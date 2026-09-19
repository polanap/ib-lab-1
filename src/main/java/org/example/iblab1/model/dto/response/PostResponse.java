package org.example.iblab1.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private Integer id;
    private String title;
    private String content;
    private OffsetDateTime createdAt;
    private AuthorResponse author;
}
