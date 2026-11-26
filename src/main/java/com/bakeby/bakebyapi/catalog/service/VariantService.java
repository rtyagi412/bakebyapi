package com.bakeby.bakebyapi.catalog.service;

import com.bakeby.bakebyapi.catalog.dto.VariantCreateRequest;
import com.bakeby.bakebyapi.catalog.dto.VariantResponse;
import com.bakeby.bakebyapi.catalog.dto.VariantUpdateRequest;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.mapper.VariantMapper;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import com.bakeby.bakebyapi.common.config.BakebyProperties;
import com.bakeby.bakebyapi.common.exception.ConflictException;
import com.bakeby.bakebyapi.common.exception.FieldValidationException;
import com.bakeby.bakebyapi.common.exception.NotFoundException;
import com.bakeby.bakebyapi.common.util.NameUtil;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variants under a product. Every variant operation first resolves the product (404 when missing) and then the
 * variant within that product (404 when it belongs to another product).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariantService {

    private static final Pattern SKU_PATTERN = Pattern.compile(VariantCreateRequest.SKU_REGEX);

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final VariantMapper variantMapper;
    private final BakebyProperties properties;

    @Transactional
    public VariantResponse create(Long productId, VariantCreateRequest request) {
        Product product = findProduct(productId);
        String sku = requireSku(request.sku());
        String label = requireLabel(request.label());
        requireValidPrices(request.priceMinor(), request.compareAtPriceMinor());
        boolean active = request.active() == null || request.active();
        requireActiveProductIfActive(active, product);
        if (variantRepository.existsBySku(sku)) {
            throw new ConflictException("Variant SKU already exists: " + sku);
        }
        Variant variant = variantMapper.toEntity(request);
        variant.setLabel(label);
        variant.setProduct(product);
        Variant saved = variantRepository.saveAndFlush(variant);
        log.info("Created variant id={} sku={} productId={}", saved.getId(), saved.getSku(), productId);
        return toResponse(saved);
    }

    /** Admin list for one product, ordered by id; includes inactive variants unless asked not to. */
    @Transactional(readOnly = true)
    public List<VariantResponse> list(Long productId, boolean includeInactive) {
        findProduct(productId);
        List<Variant> variants = includeInactive
                ? variantRepository.findByProductIdOrderByIdAsc(productId)
                : variantRepository.findByProductIdAndActiveTrueOrderByIdAsc(productId);
        return variants.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VariantResponse get(Long productId, Long variantId) {
        findProduct(productId);
        return toResponse(findVariant(productId, variantId));
    }

    @Transactional
    public VariantResponse update(Long productId, Long variantId, VariantUpdateRequest request) {
        Product product = findProduct(productId);
        Variant variant = findVariant(productId, variantId);
        String sku = requireSku(request.sku());
        String label = requireLabel(request.label());
        requireValidPrices(request.priceMinor(), request.compareAtPriceMinor());
        if (request.inStock() == null) {
            throw new FieldValidationException("inStock", "must not be null");
        }
        if (request.active() == null) {
            throw new FieldValidationException("active", "must not be null");
        }
        requireActiveProductIfActive(request.active(), product);
        if (!sku.equals(variant.getSku()) && variantRepository.existsBySku(sku)) {
            throw new ConflictException("Variant SKU already exists: " + sku);
        }
        variantMapper.updateEntity(request, variant);
        variant.setLabel(label);
        Variant saved = variantRepository.saveAndFlush(variant);
        log.info("Updated variant id={} productId={}", variantId, productId);
        return toResponse(saved);
    }

    /** Soft delete. Idempotent for already inactive variants. */
    @Transactional
    public void delete(Long productId, Long variantId) {
        findProduct(productId);
        Variant variant = findVariant(productId, variantId);
        if (variant.isActive()) {
            variant.setActive(false);
            log.info("Deleted variant id={} productId={}", variantId, productId);
        }
    }

    private VariantResponse toResponse(Variant variant) {
        return variantMapper.toResponse(variant, properties.currency());
    }

    private static String requireSku(String sku) {
        if (sku == null || sku.isEmpty()) {
            throw new FieldValidationException("sku", "must not be blank");
        }
        if (sku.length() > 64) {
            throw new FieldValidationException("sku", "size must be between 0 and 64");
        }
        if (!SKU_PATTERN.matcher(sku).matches()) {
            throw new FieldValidationException("sku", VariantCreateRequest.SKU_MESSAGE);
        }
        return sku;
    }

    private static String requireLabel(String raw) {
        String label = raw == null ? "" : NameUtil.normalize(raw);
        if (label.isEmpty()) {
            throw new FieldValidationException("label", "must not be blank");
        }
        return label;
    }

    /** {@code priceMinor >= 0}; {@code compareAtPriceMinor}, when present, must be greater than it. */
    static void requireValidPrices(Long priceMinor, Long compareAtPriceMinor) {
        if (priceMinor == null) {
            throw new FieldValidationException("priceMinor", "must not be null");
        }
        if (priceMinor < 0) {
            throw new FieldValidationException("priceMinor", "must be greater than or equal to 0");
        }
        if (compareAtPriceMinor != null && compareAtPriceMinor <= priceMinor) {
            throw new FieldValidationException("compareAtPriceMinor", "must be greater than priceMinor");
        }
    }

    /** An active variant cannot live under an inactive product (mirrors the product/category rule). */
    private static void requireActiveProductIfActive(boolean active, Product product) {
        if (active && !product.isActive()) {
            throw new ConflictException("Product " + product.getId()
                    + " is inactive; the variant cannot be active under it");
        }
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private Variant findVariant(Long productId, Long variantId) {
        return variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new NotFoundException(
                        "Variant " + variantId + " not found under product " + productId));
    }
}
