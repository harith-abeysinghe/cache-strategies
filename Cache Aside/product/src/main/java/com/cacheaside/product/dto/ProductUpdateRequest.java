package com.cacheaside.product.dto;

import com.cacheaside.product.model.Product;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductUpdateRequest {
    private String name;
    private Double price;

    public Product toProduct() {
        return Product.builder()
                .name(name)
                .price(price)
                .build();
    }
}