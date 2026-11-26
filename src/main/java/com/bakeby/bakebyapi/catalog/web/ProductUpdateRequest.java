package com.bakeby.bakebyapi.catalog.web;

import com.bakeby.bakebyapi.common.SlugUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/**
 * Full replacement. An omitted {@code slug} keeps the current slug; a null {@code images} clears the images.
 */
public record ProductUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 170) @Pattern(regexp = SlugUtil.SLUG_REGEX, message = "must be a lowercase slug (a-z, 0-9, '-')") String slug,
        @NotNull Long categoryId,
        @NotNull Boolean active,
        @NotNull @Valid ProductDetailsRequest details,
        @Size(max = 10) List<@NotBlank @Size(max = 500)
                @URL(regexp = ProductCreateRequest.HTTP_URL_REGEX, message = "must be a valid http(s) URL") String> images) {
}
