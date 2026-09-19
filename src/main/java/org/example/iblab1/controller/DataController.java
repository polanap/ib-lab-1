package org.example.iblab1.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.iblab1.mapper.PostMapper;
import org.example.iblab1.model.dto.request.PostRequest;
import org.example.iblab1.model.dto.response.PostResponse;
import org.example.iblab1.model.entity.Post;
import org.example.iblab1.service.PostService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {
    private static final int MAX_PAGE_SIZE = 100;

    private final PostService postService;
    private final PostMapper postMapper;

    @GetMapping
    public ResponseEntity<List<PostResponse>> getPosts(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        List<PostResponse> posts = postService
                .findAll(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(postMapper::toDto)
                .getContent();

        return ResponseEntity.ok(posts);
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest postRequest,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        Post createdPost = postService.createPost(postRequest, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(postMapper.toDto(createdPost));
    }
}
