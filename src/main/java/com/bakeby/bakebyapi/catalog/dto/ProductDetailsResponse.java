package com.bakeby.bakebyapi.catalog.dto;

import com.bakeby.bakebyapi.catalog.entity.Allergen;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
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
