package com.bakeby.bakebyapi.catalog.web;

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
