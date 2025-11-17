package com.cacheaside.product.controller;

import com.cacheaside.product.dto.ProductUpdateRequest;
import com.cacheaside.product.model.Product;
import com.cacheaside.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductUpdateRequest request) {

        Product updated = productService.updateProduct(id, request.toProduct());
        return ResponseEntity.ok(updated);
    }
}
