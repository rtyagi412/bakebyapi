package com.bakeby.bakebyapi.catalog.controller;

import com.bakeby.bakebyapi.catalog.dto.ProductResponse;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.service.ProductService;
import com.bakeby.bakebyapi.common.dto.PageResponse;
import com.bakeby.bakebyapi.common.util.PageRequests;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only product endpoints. Only active products in active categories, with active variants. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "slug", "createdAt");

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) DietaryType dietaryType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return productService.listPublic(category, dietaryType, PageRequests.of(page, size, sort, SORT_FIELDS));
    }

    @GetMapping("/{idOrSlug}")
    public ProductResponse get(@PathVariable String idOrSlug) {
        return productService.getPublic(idOrSlug);
    }
}
