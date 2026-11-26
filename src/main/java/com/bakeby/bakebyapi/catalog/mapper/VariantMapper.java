package com.bakeby.bakebyapi.catalog.mapper;

import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.dto.VariantCreateRequest;
import com.bakeby.bakebyapi.catalog.dto.VariantResponse;
import com.bakeby.bakebyapi.catalog.dto.VariantUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface VariantMapper {

    /** The product is resolved by the service. Null {@code inStock}/{@code active} default to true (entity builder). */
    @Mapping(target = "product", ignore = true)
    Variant toEntity(VariantCreateRequest request);

    /** The currency is the single configured value ({@code bakeby.currency}), supplied by the caller. */
    @Mapping(target = "id", source = "variant.id")
    @Mapping(target = "currency", source = "currency")
    VariantResponse toResponse(Variant variant, String currency);

    /** PUT is full replacement, so an omitted compare-at price is cleared. The owning product never changes. */
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(VariantUpdateRequest request, @MappingTarget Variant variant);
}
