package com.bakeby.bakebyapi.catalog.web;

import com.bakeby.bakebyapi.catalog.domain.Allergen;
import com.bakeby.bakebyapi.catalog.domain.DietaryType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Product details in create and update bodies. A null {@code allergens} means none. */
public record ProductDetailsRequest(
        @Size(max = 5000) String description,
        @Size(max = 5000) String ingredients,
        Set<@NotNull Allergen> allergens,
        @NotNull DietaryType dietaryType,
        @PositiveOrZero Integer shelfLifeDays,
        @PositiveOrZero Integer leadTimeHours,
        @Min(1) Integer serves) {
}
