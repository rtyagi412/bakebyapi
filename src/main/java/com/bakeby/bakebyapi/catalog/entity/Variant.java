package com.bakeby.bakebyapi.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "variant")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Variant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ToString.Include
    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(name = "compare_at_price_minor")
    private Long compareAtPriceMinor;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock = true;

    @Column(nullable = false)
    private boolean active = true;

    @Builder
    private Variant(Product product, String sku, String label, long priceMinor, Long compareAtPriceMinor,
                    Boolean inStock, Boolean active) {
        this.product = product;
        this.sku = sku;
        this.label = label;
        this.priceMinor = priceMinor;
        this.compareAtPriceMinor = compareAtPriceMinor;
        this.inStock = inStock == null || inStock;
        this.active = active == null || active;
    }
}
