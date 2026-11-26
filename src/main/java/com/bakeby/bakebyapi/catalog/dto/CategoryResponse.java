package com.bakeby.bakebyapi.catalog.dto;

public record CategoryResponse(Long id, String name, String slug, String description, boolean active) {
}
