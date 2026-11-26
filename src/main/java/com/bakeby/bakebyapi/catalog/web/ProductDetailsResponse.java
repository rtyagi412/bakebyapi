package com.bakeby.bakebyapi.catalog.web;

import com.bakeby.bakebyapi.catalog.domain.Allergen;
import com.bakeby.bakebyapi.catalog.domain.DietaryType;
import java.util.List;

public record ProductDetailsResponse(
        String description,
        String ingredients,
        List<Allergen> allergens,
        DietaryType dietaryType,
        Integer shelfLifeDays,
        Integer leadTimeHours,
        Integer serves) {
}
