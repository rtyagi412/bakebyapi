package com.bakeby.bakebyapi.catalog.service;

import com.bakeby.bakebyapi.catalog.domain.Category;
import com.bakeby.bakebyapi.catalog.mapper.CategoryMapper;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.web.CategoryCreateRequest;
import com.bakeby.bakebyapi.catalog.web.CategoryResponse;
import com.bakeby.bakebyapi.catalog.web.CategoryUpdateRequest;
import com.bakeby.bakebyapi.common.BadRequestException;
import com.bakeby.bakebyapi.common.ConflictException;
import com.bakeby.bakebyapi.common.NameUtil;
import com.bakeby.bakebyapi.common.NotFoundException;
import com.bakeby.bakebyapi.common.PageResponse;
import com.bakeby.bakebyapi.common.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = NameUtil.normalize(request.name());
        String slug;
        if (request.slug() != null) {
            slug = SlugUtil.requireValid(request.slug());
        } else {
            slug = SlugUtil.slugify(name);
            if (!SlugUtil.isValid(slug)) {
                throw new BadRequestException("Cannot derive a slug from the name; supply a slug");
            }
        }
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Category name already exists: " + name);
        }
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category slug already exists: " + slug);
        }
        Category category = categoryMapper.toEntity(request);
        category.setName(name);
        category.setSlug(slug);
        Category saved = categoryRepository.saveAndFlush(category);
        log.info("Created category id={}", saved.getId());
        return categoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(boolean includeInactive, Pageable pageable) {
        Page<Category> page = includeInactive
                ? categoryRepository.findAll(pageable)
                : categoryRepository.findByActiveTrue(pageable);
        return PageResponse.from(page.map(categoryMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return categoryMapper.toResponse(find(id));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = find(id);
        String name = NameUtil.normalize(request.name());
        if (request.slug() != null) {
            SlugUtil.requireValid(request.slug());
        }
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Category name already exists: " + name);
        }
        if (request.slug() != null && !request.slug().equals(category.getSlug())
                && categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Category slug already exists: " + request.slug());
        }
        if (category.isActive() && Boolean.FALSE.equals(request.active())) {
            requireNoActiveProducts(id);
        }
        categoryMapper.updateEntity(request, category);
        category.setName(name);
        if (request.slug() != null) {
            category.setSlug(request.slug());
        }
        Category saved = categoryRepository.saveAndFlush(category);
        log.info("Updated category id={}", id);
        return categoryMapper.toResponse(saved);
    }

    /** Soft delete. Idempotent for already inactive categories. */
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (!category.isActive()) {
            return;
        }
        requireNoActiveProducts(id);
        category.setActive(false);
        log.info("Deleted category id={}", id);
    }

    private void requireNoActiveProducts(Long id) {
        if (productRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new ConflictException("Category has active products and cannot be deactivated or deleted");
        }
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }
}
