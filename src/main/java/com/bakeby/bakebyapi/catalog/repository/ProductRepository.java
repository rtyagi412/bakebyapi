package com.bakeby.bakebyapi.catalog.repository;

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
