package org.example.iblab1.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @NotBlank(message = "Title cannot be empty")
    @Size(max = 200, message = "Title must not be longer than 200 characters")
    private String title;

    @NotBlank(message = "Content cannot be empty")
    @Size(max = 10000, message = "Content must not be longer than 10000 characters")
    private String content;
}
