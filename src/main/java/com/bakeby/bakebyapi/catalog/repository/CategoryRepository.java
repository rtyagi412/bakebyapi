package com.bakeby.bakebyapi.catalog.repository;

import com.bakeby.bakebyapi.catalog.domain.Category;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Category> findByActiveTrue(Pageable pageable);

    // Category names are unique ignoring case; backed by the unique index on lower(name) (V2).
    boolean existsByNameIgnoreCase(String name);

    // For updates: a category may keep its own name (or change its casing).
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
