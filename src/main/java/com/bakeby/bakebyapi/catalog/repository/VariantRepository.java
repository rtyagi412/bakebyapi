package com.bakeby.bakebyapi.catalog.repository;

import com.bakeby.bakebyapi.catalog.domain.Variant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findByProductIdAndSku(Long productId, String sku);

    boolean existsBySku(String sku);

    List<Variant> findByProductId(Long productId);

    List<Variant> findByProductIdAndActiveTrue(Long productId);

    List<Variant> findByProductIdInOrderByIdAsc(Collection<Long> productIds);
}
