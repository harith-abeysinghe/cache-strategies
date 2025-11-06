package com.writethrough.order.service;

import com.writethrough.order.model.Order;

import java.util.List;

public interface OrderService {

    Order createOrder(Order order);

    Order updateOrder(Long id, Order order);

    Order getOrder(Long id);

    List<Order> getAllOrders();

    void deleteOrder(Long id);

}

