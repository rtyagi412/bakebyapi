package com.bakeby.bakebyapi.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetails {

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ingredients", columnDefinition = "text")
    private String ingredients;

    @Enumerated(EnumType.STRING)
    @Column(name = "dietary_type", nullable = false, length = 20)
    private DietaryType dietaryType;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "lead_time_hours")
    private Integer leadTimeHours;

    @Column(name = "serves")
    private Integer serves;
}
