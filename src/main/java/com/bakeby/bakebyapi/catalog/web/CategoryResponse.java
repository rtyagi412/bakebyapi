package com.bakeby.bakebyapi.catalog.web;

public record CategoryResponse(Long id, String name, String slug, String description, boolean active) {
}
