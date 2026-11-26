package com.bakeby.bakebyapi.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Prices are integer minor units (paise). {@code compareAtPriceMinor} is optional and must be greater than
 * {@code priceMinor} (checked by the service). A null {@code inStock} or {@code active} defaults to true.
 */
public record VariantCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = VariantCreateRequest.SKU_REGEX,
                message = VariantCreateRequest.SKU_MESSAGE) String sku,
        @NotBlank @Size(max = 100) String label,
        @NotNull @PositiveOrZero Long priceMinor,
        @PositiveOrZero Long compareAtPriceMinor,
        Boolean inStock,
        Boolean active) {

    public static final String SKU_REGEX = "^[A-Za-z0-9._-]+$";
    public static final String SKU_MESSAGE = "must contain only letters, digits, '.', '_' or '-'";
}
