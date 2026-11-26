package com.bakeby.bakebyapi.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application settings under the {@code bakeby} prefix. */
@Validated
@ConfigurationProperties(prefix = "bakeby")
public record BakebyProperties(@NotBlank String currency) {
}
