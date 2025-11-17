package com.cacheaside.product.service;

import com.cacheaside.product.model.Product;
import com.cacheaside.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Product> redisTemplate;

    private static final String CACHE_PREFIX = "product:";

    public Product getProduct(Long id) {

        String cacheKey = CACHE_PREFIX + id;

        // Try Cache
        long cacheStart = System.nanoTime();
        Object cachedObj = redisTemplate.opsForValue().get(cacheKey);
        long cacheEnd = System.nanoTime();

        if (cachedObj != null) {
            log.info("CACHE HIT for product {} | Time: {} ms",
                    id, (cacheEnd - cacheStart) / 1_000_000.0);

            // Some serializers (or previously cached entries) may return a Map/LinkedHashMap
            // when type information isn't present. Convert it to Product to avoid ClassCastException.
            if (cachedObj instanceof Product) {
                return (Product) cachedObj;
            }

            if (cachedObj instanceof Map) {
                // Convert Map to Product using Jackson ObjectMapper
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                Product product = mapper.convertValue(cachedObj, Product.class);

                // Re-cache as a proper Product so subsequent reads return Product directly
                redisTemplate.opsForValue().set(cacheKey, product, Duration.ofMinutes(30));

                return product;
            }

            // Unexpected cached type; log and fallthrough to DB fetch
            log.warn("Unexpected cached type for key {}: {}", cacheKey, cachedObj.getClass());
        }

        log.info("CACHE MISS for product {} | Cache lookup time: {} ms",
                id, (cacheEnd - cacheStart) / 1_000_000.0);

        // Fetch From DB
        long dbStart = System.nanoTime();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        long dbEnd = System.nanoTime();

        log.info("DB FETCH for product {} | Time: {} ms",
                id, (dbEnd - dbStart) / 1_000_000.0);

        // Write Back to Cache
        redisTemplate.opsForValue().set(cacheKey, product, Duration.ofMinutes(30));

        return product;
    }

    public Product updateProduct(Long id, Product update) {

        long dbStart = System.nanoTime();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        long dbEnd = System.nanoTime();

        log.info("DB FETCH (update) for product {} | Time: {} ms",
                id, (dbEnd - dbStart) / 1_000_000.0);

        product.setName(update.getName());
        product.setPrice(update.getPrice());

        long saveStart = System.nanoTime();
        Product saved = productRepository.save(product);
        long saveEnd = System.nanoTime();

        log.info("DB SAVE for product {} | Time: {} ms",
                id, (saveEnd - saveStart) / 1_000_000.0);

        // Invalidate Cache Entry
        redisTemplate.delete(CACHE_PREFIX + id);
        log.info("CACHE INVALIDATED for product {}", id);

        return saved;
    }

}
