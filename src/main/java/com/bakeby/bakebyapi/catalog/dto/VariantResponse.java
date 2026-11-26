package com.bakeby.bakebyapi.catalog.dto;

public record VariantResponse(
        Long id,
        String sku,
        String label,
        long priceMinor,
        Long compareAtPriceMinor,
        String currency,
        boolean inStock,
        boolean active) {
}
