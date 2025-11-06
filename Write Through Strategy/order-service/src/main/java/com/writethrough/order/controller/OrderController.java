package com.writethrough.order.controller;

import com.writethrough.order.model.Order;
import com.writethrough.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        log.info("Received request to create order");
        Order created = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id, @RequestBody Order order) {
        log.info("Received request to update order id: {}", id);
        Order updated = orderService.updateOrder(id, order);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        log.info("Received request to get order id: {}", id);
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        log.info("Received request to fetch all orders");
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        log.info("Received request to delete order id: {}", id);
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}

