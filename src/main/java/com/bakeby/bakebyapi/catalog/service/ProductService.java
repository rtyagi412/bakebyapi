package com.bakeby.bakebyapi.catalog.service;

import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.mapper.ProductMapper;
import com.bakeby.bakebyapi.catalog.mapper.VariantMapper;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import com.bakeby.bakebyapi.catalog.dto.ProductCreateRequest;
import com.bakeby.bakebyapi.catalog.dto.ProductResponse;
import com.bakeby.bakebyapi.catalog.dto.ProductUpdateRequest;
import com.bakeby.bakebyapi.catalog.dto.VariantResponse;
import com.bakeby.bakebyapi.common.exception.BadRequestException;
import com.bakeby.bakebyapi.common.config.BakebyProperties;
import com.bakeby.bakebyapi.common.exception.ConflictException;
import com.bakeby.bakebyapi.common.exception.FieldValidationException;
import com.bakeby.bakebyapi.common.util.NameUtil;
import com.bakeby.bakebyapi.common.exception.NotFoundException;
import com.bakeby.bakebyapi.common.dto.PageResponse;
import com.bakeby.bakebyapi.common.util.IdOrSlug;
import com.bakeby.bakebyapi.common.util.SlugUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final VariantRepository variantRepository;
    private final ProductMapper productMapper;
    private final VariantMapper variantMapper;
    private final BakebyProperties properties;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        String name = requireName(request.name());
        if (request.slug() != null) {
            SlugUtil.requireValid(request.slug());
        }
        Category category = requireCategory(request.categoryId());
        boolean active = request.active() == null || request.active();
        requireActiveCategoryIfActive(active, category);
        String slug;
        if (request.slug() != null) {
            if (productRepository.existsBySlug(request.slug())) {
                throw new ConflictException("Product slug already exists: " + request.slug());
            }
            slug = request.slug();
        } else {
            slug = nextFreeSlug(name);
        }
        Product product = productMapper.toEntity(request);
        product.setName(name);
        product.setSlug(slug);
        product.setCategory(category);
        Product saved = productRepository.saveAndFlush(product);
        log.info("Created product id={} slug={}", saved.getId(), saved.getSlug());
        return productMapper.toResponse(saved, List.of());
    }

    /** Admin list: pages products in the database, then loads images, allergens and variants for that page. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(boolean includeInactive, Pageable pageable) {
        Page<Product> page = includeInactive
                ? productRepository.findAll(pageable)
                : productRepository.findByActiveTrue(pageable);
        List<ProductResponse> content = toResponses(page.getContent(), false);
        return PageResponse.from(new PageImpl<>(content, page.getPageable(), page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponses(List.of(find(id)), false).getFirst();
    }

    /**
     * Public list: active products in active categories, embedding only active variants. Products are paged in
     * SQL, then images, allergens and active variants are loaded for that page with one query each.
     *
     * @param category    optional category id (all digits) or slug; an unknown or inactive category is 404
     * @param dietaryType optional dietary type filter
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listPublic(String category, DietaryType dietaryType, Pageable pageable) {
        Long categoryId = category == null ? null : IdOrSlug.resolve(category,
                        categoryRepository::findByIdAndActiveTrue, categoryRepository::findBySlugAndActiveTrue)
                .map(Category::getId)
                .orElseThrow(() -> new NotFoundException("Category not found: " + category));
        Page<Product> page = productRepository.findPublic(categoryId, dietaryType, pageable);
        List<ProductResponse> content = toResponses(page.getContent(), true);
        return PageResponse.from(new PageImpl<>(content, page.getPageable(), page.getTotalElements()));
    }

    /** Public lookup by id (all-digit value) or slug; missing, inactive or in an inactive category is 404. */
    @Transactional(readOnly = true)
    public ProductResponse getPublic(String idOrSlug) {
        Product product = IdOrSlug.resolve(idOrSlug,
                        productRepository::findByIdAndActiveTrueAndCategoryActiveTrue,
                        productRepository::findBySlugAndActiveTrueAndCategoryActiveTrue)
                .orElseThrow(() -> new NotFoundException("Product not found: " + idOrSlug));
        return toResponses(List.of(product), true).getFirst();
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = find(id);
        String name = requireName(request.name());
        if (request.slug() != null) {
            SlugUtil.requireValid(request.slug());
        }
        Category category = requireCategory(request.categoryId());
        requireActiveCategoryIfActive(request.active(), category);
        if (request.slug() != null && !request.slug().equals(product.getSlug())
                && productRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Product slug already exists: " + request.slug());
        }
        boolean deactivating = product.isActive() && !request.active();
        productMapper.updateEntity(request, product);
        product.setName(name);
        product.setCategory(category);
        if (request.slug() != null) {
            product.setSlug(request.slug());
        }
        if (deactivating) {
            deactivateVariants(id);
        }
        Product saved = productRepository.saveAndFlush(product);
        log.info("Updated product id={}", id);
        return toResponses(List.of(saved), false).getFirst();
    }

    /** Soft delete that also deactivates the product's variants. Idempotent for already inactive products. */
    @Transactional
    public void delete(Long id) {
        Product product = find(id);
        deactivateVariants(id);
        if (product.isActive()) {
            product.setActive(false);
            log.info("Deleted product id={}", id);
        }
    }

    private void deactivateVariants(Long productId) {
        List<Variant> variants = variantRepository.findByProductIdAndActiveTrue(productId);
        variants.forEach(v -> v.setActive(false));
        if (!variants.isEmpty()) {
            log.info("Deactivated {} variant(s) of product id={}", variants.size(), productId);
        }
    }

    /**
     * Maps products to responses with a fixed number of queries regardless of how many products there are:
     * one each for images, allergens and variants (all variants for admin views, active ones only for public).
     */
    private List<ProductResponse> toResponses(List<Product> products, boolean activeVariantsOnly) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        productRepository.fetchImages(ids);
        productRepository.fetchAllergens(ids);
        String currency = properties.currency();
        List<Variant> variants = activeVariantsOnly
                ? variantRepository.findByProductIdInAndActiveTrueOrderByIdAsc(ids)
                : variantRepository.findByProductIdInOrderByIdAsc(ids);
        Map<Long, List<VariantResponse>> variantsByProduct = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId(),
                        Collectors.mapping(v -> variantMapper.toResponse(v, currency), Collectors.toList())));
        return products.stream()
                .map(p -> productMapper.toResponse(p, variantsByProduct.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** Derives a slug from the name; on collision appends -2, -3, ... (the lowest free suffix). */
    private String nextFreeSlug(String name) {
        String base = SlugUtil.slugify(name);
        if (!SlugUtil.isValid(base)) {
            throw new BadRequestException("Cannot derive a slug from the name; supply a slug");
        }
        Set<String> taken = new HashSet<>(productRepository.findSlugsWithBase(base));
        if (!taken.contains(base)) {
            return base;
        }
        int n = 2;
        while (taken.contains(base + "-" + n)) {
            n++;
        }
        return base + "-" + n;
    }

    private static String requireName(String raw) {
        String name = NameUtil.normalize(raw);
        if (name.isEmpty()) {
            throw new FieldValidationException("name", "must not be blank");
        }
        return name;
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new FieldValidationException("categoryId", "category does not exist"));
    }

    private static void requireActiveCategoryIfActive(boolean active, Category category) {
        if (active && !category.isActive()) {
            throw new ConflictException("Category " + category.getId()
                    + " is inactive; the product cannot be active in it");
        }
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }
}
