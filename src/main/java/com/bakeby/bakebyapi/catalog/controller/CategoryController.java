package com.bakeby.bakebyapi.catalog.controller;

import com.bakeby.bakebyapi.catalog.dto.CategoryResponse;
import com.bakeby.bakebyapi.catalog.service.CategoryService;
import com.bakeby.bakebyapi.common.dto.PageResponse;
import com.bakeby.bakebyapi.common.util.PageRequests;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only category endpoints. Only active categories are visible. */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "slug", "createdAt");

    private final CategoryService categoryService;

    @GetMapping
    public PageResponse<CategoryResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return categoryService.listPublic(PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @GetMapping("/{idOrSlug}")
    public CategoryResponse get(@PathVariable String idOrSlug) {
        return categoryService.getPublic(idOrSlug);
    }
}
