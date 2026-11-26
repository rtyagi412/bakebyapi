package com.bakeby.bakebyapi.catalog.service;

import com.bakeby.bakebyapi.catalog.domain.Category;
import com.bakeby.bakebyapi.catalog.mapper.CategoryMapper;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.web.CategoryCreateRequest;
import com.bakeby.bakebyapi.catalog.web.CategoryResponse;
import com.bakeby.bakebyapi.catalog.web.CategoryUpdateRequest;
import com.bakeby.bakebyapi.common.ConflictException;
import com.bakeby.bakebyapi.common.NotFoundException;
import com.bakeby.bakebyapi.common.SlugUtil;
import lombok.RequiredArgsConstructor;
import com.bakeby.bakebyapi.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Category name already exists: " + request.name());
        }
        String slug = request.slug() != null ? request.slug() : SlugUtil.slugify(request.name());
        SlugUtil.requireValid(slug);
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category slug already exists: " + slug);
        }
        Category category = categoryMapper.toEntity(request);
        category.setSlug(slug);
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
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
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("Category name already exists: " + request.name());
        }
        if (request.slug() != null && !request.slug().equals(category.getSlug())) {
            SlugUtil.requireValid(request.slug());
            if (categoryRepository.existsBySlug(request.slug())) {
                throw new ConflictException("Category slug already exists: " + request.slug());
            }
            category.setSlug(request.slug());
        }
        categoryMapper.updateEntity(request, category);
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    /** Soft delete. Idempotent for already inactive categories. */
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (!category.isActive()) {
            return;
        }
        if (productRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new ConflictException("Category has active products and cannot be deleted");
        }
        category.setActive(false);
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }
}
