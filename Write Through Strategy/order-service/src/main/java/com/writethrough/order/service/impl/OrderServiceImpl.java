package com.writethrough.order.service.impl;

import com.writethrough.order.model.Order;
import com.writethrough.order.repository.OrderRepository;
import com.writethrough.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Order> redisTemplate;

    private String keyFor(Long id) {
        return "order:" + id;
    }

    @Override
    public Order createOrder(Order order) {
        log.info("Creating order for customer={}, product={}", order.getCustomerName(), order.getProduct());
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        Order saved = orderRepository.save(order);
        // write-through: after writing to DB, write to cache
        try {
            redisTemplate.opsForValue().set(keyFor(saved.getId()), saved);
            log.debug("Cached order id={}", saved.getId());
        } catch (Exception e) {
            log.warn("Failed to write order to cache (id={}), continuing", saved.getId(), e);
        }
        return saved;
    }

    @Override
    public Order updateOrder(Long id, Order order) {
        log.info("Updating order id={}", id);
        Order existing = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));

        // update fields - keeping id and createdAt
        existing.setCustomerName(order.getCustomerName());
        existing.setProduct(order.getProduct());
        existing.setQuantity(order.getQuantity());
        existing.setPrice(order.getPrice());
        existing.setStatus(order.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());

        Order saved = orderRepository.save(existing);
        // write-through: update cache after DB write
        try {
            redisTemplate.opsForValue().set(keyFor(saved.getId()), saved);
            log.debug("Updated cache for order id={}", saved.getId());
        } catch (Exception e) {
            log.warn("Failed to update cache for order id={}, continuing", saved.getId(), e);
        }

        return saved;
    }

    @Override
    public Order getOrder(Long id) {
        log.debug("Getting order id={} (attempting cache first)", id);
        String key = keyFor(id);
        try {
            Order cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for order id={}", id);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache for id={}, falling back to DB", id, e);
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));

        // populate cache after DB read
        try {
            redisTemplate.opsForValue().set(key, order);
            log.debug("Cached order id={} after DB read", id);
        } catch (Exception e) {
            log.warn("Failed to cache order id={} after DB read", id, e);
        }

        return order;
    }

    @Override
    public List<Order> getAllOrders() {
        log.debug("Fetching all orders from DB");
        List<Order> orders = orderRepository.findAll();
        // populate cache for write-through/read efficiency
        try {
            for (Order o : orders) {
                if (o.getId() != null) {
                    redisTemplate.opsForValue().set(keyFor(o.getId()), o);
                }
            }
            log.debug("Populated cache for {} orders", orders.size());
        } catch (Exception e) {
            log.warn("Failed to populate cache for orders", e);
        }
        return orders;
    }

    @Override
    public void deleteOrder(Long id) {
        log.info("Deleting order id={}", id);
        if (!orderRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
        try {
            redisTemplate.delete(keyFor(id));
            log.debug("Evicted cache for order id={}", id);
        } catch (Exception e) {
            log.warn("Failed to evict cache for order id={}, continuing", id, e);
        }
    }
}
