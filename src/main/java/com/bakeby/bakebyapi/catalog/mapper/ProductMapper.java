package com.bakeby.bakebyapi.catalog.mapper;

import com.bakeby.bakebyapi.catalog.domain.Allergen;
import com.bakeby.bakebyapi.catalog.domain.Product;
import com.bakeby.bakebyapi.catalog.domain.ProductDetails;
import com.bakeby.bakebyapi.catalog.web.ProductCreateRequest;
import com.bakeby.bakebyapi.catalog.web.ProductDetailsRequest;
import com.bakeby.bakebyapi.catalog.web.ProductDetailsResponse;
import com.bakeby.bakebyapi.catalog.web.ProductResponse;
import com.bakeby.bakebyapi.catalog.web.ProductUpdateRequest;
import com.bakeby.bakebyapi.catalog.web.VariantResponse;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    /** Slug and category are resolved by the service. */
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "allergens", source = "details.allergens")
    Product toEntity(ProductCreateRequest request);

    /**
     * Slug changes only when supplied and the category is resolved, so the service handles both.
     * PUT is full replacement: omitted images or allergens are cleared.
     */
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "allergens", source = "details.allergens",
            defaultExpression = "java(java.util.EnumSet.noneOf(Allergen.class))")
    @Mapping(target = "images", source = "images", defaultExpression = "java(new java.util.ArrayList<>())")
    void updateEntity(ProductUpdateRequest request, @MappingTarget Product product);

    ProductDetails toDetails(ProductDetailsRequest request);

    /** Variants are loaded separately by the caller (batched across a page of products). */
    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "details", source = "product")
    @Mapping(target = "images", source = "product.images")
    @Mapping(target = "variants", source = "variants")
    ProductResponse toResponse(Product product, List<VariantResponse> variants);

    @Mapping(target = "description", source = "details.description")
    @Mapping(target = "ingredients", source = "details.ingredients")
    @Mapping(target = "dietaryType", source = "details.dietaryType")
    @Mapping(target = "shelfLifeDays", source = "details.shelfLifeDays")
    @Mapping(target = "leadTimeHours", source = "details.leadTimeHours")
    @Mapping(target = "serves", source = "details.serves")
    ProductDetailsResponse toDetailsResponse(Product product);

    /** Stable output order (enum declaration order); the persistent set is unordered. */
    default List<Allergen> toAllergenList(Set<Allergen> allergens) {
        return allergens == null ? List.of() : allergens.stream().sorted().toList();
    }
}
