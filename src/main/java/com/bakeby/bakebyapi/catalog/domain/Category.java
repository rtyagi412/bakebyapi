package com.bakeby.bakebyapi.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Category extends BaseEntity {

    @ToString.Include
    @Column(nullable = false, length = 100)
    private String name;

    @ToString.Include
    @Column(nullable = false, length = 120)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Builder
    private Category(String name, String slug, String description, Boolean active) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.active = active == null || active;
    }
}
