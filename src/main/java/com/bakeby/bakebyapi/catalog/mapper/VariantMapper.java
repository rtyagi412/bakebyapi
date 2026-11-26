package com.bakeby.bakebyapi.catalog.mapper;

import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.dto.VariantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VariantMapper {

    /** The currency is the single configured value ({@code bakeby.currency}), supplied by the caller. */
    @Mapping(target = "id", source = "variant.id")
    @Mapping(target = "currency", source = "currency")
    VariantResponse toResponse(Variant variant, String currency);
}
