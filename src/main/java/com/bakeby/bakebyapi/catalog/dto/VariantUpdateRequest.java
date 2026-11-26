package com.bakeby.bakebyapi.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Full replacement: an omitted {@code compareAtPriceMinor} is cleared. */
public record VariantUpdateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = VariantCreateRequest.SKU_REGEX,
                message = VariantCreateRequest.SKU_MESSAGE) String sku,
        @NotBlank @Size(max = 100) String label,
        @NotNull @PositiveOrZero Long priceMinor,
        @PositiveOrZero Long compareAtPriceMinor,
        @NotNull Boolean inStock,
        @NotNull Boolean active) {
}
