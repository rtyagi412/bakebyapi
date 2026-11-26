package com.bakeby.bakebyapi.catalog.controller;

import com.bakeby.bakebyapi.catalog.dto.VariantCreateRequest;
import com.bakeby.bakebyapi.catalog.dto.VariantResponse;
import com.bakeby.bakebyapi.catalog.dto.VariantUpdateRequest;
import com.bakeby.bakebyapi.catalog.service.VariantService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/variants")
@RequiredArgsConstructor
public class AdminVariantController {

    private final VariantService variantService;

    @PostMapping
    public ResponseEntity<VariantResponse> create(@PathVariable Long productId,
            @Valid @RequestBody VariantCreateRequest request) {
        VariantResponse created = variantService.create(productId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<VariantResponse> list(@PathVariable Long productId,
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return variantService.list(productId, includeInactive);
    }

    @GetMapping("/{variantId}")
    public VariantResponse get(@PathVariable Long productId, @PathVariable Long variantId) {
        return variantService.get(productId, variantId);
    }

    @PutMapping("/{variantId}")
    public VariantResponse update(@PathVariable Long productId, @PathVariable Long variantId,
            @Valid @RequestBody VariantUpdateRequest request) {
        return variantService.update(productId, variantId, request);
    }

    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long variantId) {
        variantService.delete(productId, variantId);
        return ResponseEntity.noContent().build();
    }
}
