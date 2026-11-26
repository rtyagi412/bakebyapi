package com.bakeby.bakebyapi.catalog.repository;

import com.bakeby.bakebyapi.catalog.domain.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
}
