package com.bakeby.bakebyapi.catalog.web;

import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String slug,
        Long categoryId,
        boolean active,
        ProductDetailsResponse details,
        List<String> images,
        List<VariantResponse> variants) {
}
