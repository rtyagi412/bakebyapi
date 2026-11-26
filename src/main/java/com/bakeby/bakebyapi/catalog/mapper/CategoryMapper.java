package com.bakeby.bakebyapi.catalog.mapper;

import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.dto.CategoryCreateRequest;
import com.bakeby.bakebyapi.catalog.dto.CategoryResponse;
import com.bakeby.bakebyapi.catalog.dto.CategoryUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    /** Slug is resolved by the service. */
    @Mapping(target = "slug", ignore = true)
    Category toEntity(CategoryCreateRequest request);

    CategoryResponse toResponse(Category category);

    /**
     * Slug changes only when supplied, so the service handles it.
     * PUT is full replacement, so an omitted description is cleared.
     */
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CategoryUpdateRequest request, @MappingTarget Category category);
}
