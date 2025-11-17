package com.writethrough.order.service.impl;

import com.writethrough.order.model.Order;
import com.writethrough.order.repository.OrderRepository;
import com.writethrough.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Order> redisTemplate;

    private String cacheKey(Long id) {
        return "order:" + id;
    }

    @Override
    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating order for customer: {}, product: {}", order.getCustomerName(), order.getProduct());

        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // 1️⃣ Write to cache first (cache owns the write path)
        String tempKey = "order:pending";
        long redisStart = System.nanoTime();
        redisTemplate.opsForValue().set(tempKey, order);
        long redisDuration = (System.nanoTime() - redisStart) / 1_000_000;
        log.info("Cache pre-write complete ({} ms)", redisDuration);

        // 2️⃣ Synchronously write to DB
        long dbStart = System.nanoTime();
        Order savedOrder = orderRepository.save(order);
        long dbDuration = (System.nanoTime() - dbStart) / 1_000_000;
        log.info("DB write complete for order id: {} ({} ms)", savedOrder.getId(), dbDuration);

        // 3️⃣ Replace temp cache with final key
        redisTemplate.delete(tempKey);
        redisTemplate.opsForValue().set(cacheKey(savedOrder.getId()), savedOrder);
        log.info("Cache finalized for order id: {}", savedOrder.getId());

        return savedOrder;
    }

    @Override
    @Transactional
    public Order updateOrder(Long id, Order order) {
        log.info("Updating order id: {}", id);

        Order existing = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));

        existing.setCustomerName(order.getCustomerName());
        existing.setProduct(order.getProduct());
        existing.setQuantity(order.getQuantity());
        existing.setPrice(order.getPrice());
        existing.setStatus(order.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());

        // 1️⃣ Cache first
        String key = cacheKey(id);
        redisTemplate.opsForValue().set(key, existing);
        log.info("Cache updated for order id: {}", id);

        // 2️⃣ Write to DB (synchronously)
        Order savedOrder = orderRepository.save(existing);
        log.info("DB write-through complete for order id: {}", id);

        return savedOrder;
    }

    @Override
    public Order getOrder(Long id) {
        String key = cacheKey(id);
        try {
            Order cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for order id: {}", id);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Cache read failed for order id: {}, falling back to DB", id, e);
        }

        log.debug("Cache miss for order id: {}, querying DB", id);
        Order dbOrder = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));

        // Repopulate cache
        redisTemplate.opsForValue().set(key, dbOrder);
        return dbOrder;
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        log.info("Deleting order id: {}", id);
        if (!orderRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id);
        }

        // Delete in both layers synchronously
        orderRepository.deleteById(id);
        redisTemplate.delete(cacheKey(id));
        log.info("Deleted order id: {} from both DB and cache", id);
    }

    @Override
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        for (Order o : orders) {
            redisTemplate.opsForValue().set(cacheKey(o.getId()), o);
        }
        return orders;
    }
}
