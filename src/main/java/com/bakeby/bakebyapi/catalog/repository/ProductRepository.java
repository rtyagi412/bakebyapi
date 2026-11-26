package com.bakeby.bakebyapi.catalog.repository;

import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);

    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Public list: active products whose category is active, optionally filtered by category id and dietary type
     * (a null filter is not applied). Pages products only (no collection fetch), so paging happens in SQL.
     */
    @Query(value = """
            select p from Product p join p.category c
            where p.active = true and c.active = true
              and (:categoryId is null or c.id = :categoryId)
              and (:dietaryType is null or p.details.dietaryType = :dietaryType)
            """,
            countQuery = """
            select count(p) from Product p join p.category c
            where p.active = true and c.active = true
              and (:categoryId is null or c.id = :categoryId)
              and (:dietaryType is null or p.details.dietaryType = :dietaryType)
            """)
    Page<Product> findPublic(@Param("categoryId") Long categoryId, @Param("dietaryType") DietaryType dietaryType,
                             Pageable pageable);

    /** Public lookup: the product and its category must both be active. */
    Optional<Product> findByIdAndActiveTrueAndCategoryActiveTrue(Long id);

    /** Public lookup: the product and its category must both be active. */
    Optional<Product> findBySlugAndActiveTrueAndCategoryActiveTrue(String slug);

    /** Slugs equal to {@code base} or of the form {@code base-...}; used to pick the next free numeric suffix. */
    @Query("select p.slug from Product p where p.slug = :base or p.slug like concat(:base, '-%')")
    List<String> findSlugsWithBase(@Param("base") String base);

    /**
     * Initializes the images of already loaded products in one query. Paging is done on products first, so
     * this collection fetch join never paginates in memory.
     */
    @Query("select distinct p from Product p left join fetch p.images where p.id in :ids")
    List<Product> fetchImages(@Param("ids") Collection<Long> ids);

    /** Initializes the allergens of already loaded products in one query (see {@link #fetchImages}). */
    @Query("select distinct p from Product p left join fetch p.allergens where p.id in :ids")
    List<Product> fetchAllergens(@Param("ids") Collection<Long> ids);
}
