package com.bakeby.bakebyapi.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ToString.Include
    @Column(nullable = false, length = 150)
    private String name;

    @ToString.Include
    @Column(nullable = false, length = 170)
    private String slug;

    @Embedded
    private ProductDetails details = new ProductDetails();

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "product_allergen", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "allergen", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Allergen> allergens = EnumSet.noneOf(Allergen.class);

    @ElementCollection
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 500)
    private List<String> images = new ArrayList<>();

    @Builder
    private Product(Category category, String name, String slug, ProductDetails details, Boolean active,
                    Set<Allergen> allergens, List<String> images) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.details = details == null ? new ProductDetails() : details;
        this.active = active == null || active;
        if (allergens != null) {
            this.allergens.addAll(allergens);
        }
        if (images != null) {
            this.images.addAll(images);
        }
    }
}
