package com.bakeby.bakebyapi.catalog.web;

import com.bakeby.bakebyapi.common.SlugUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 120) @Pattern(regexp = SlugUtil.SLUG_REGEX, message = "must be a lowercase slug (a-z, 0-9, '-')") String slug,
        String description,
        Boolean active) {
}
