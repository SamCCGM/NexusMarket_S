package application.service;

import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.repository.OrderRepository;
import application.persistence.mysql.repository.OrderItemRepository;
import application.domain.enums.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    public OrderEntity save(OrderEntity order) {
        return orderRepository.save(order);
    }

    public Optional<OrderEntity> findById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<OrderEntity> findByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    public List<OrderEntity> findByBuyerId(Long buyerId) {
        return orderRepository.findByBuyerId(buyerId);
    }

    public Page<OrderEntity> findByBuyerId(Long buyerId, Pageable pageable) {
        return orderRepository.findByBuyerId(buyerId, pageable);
    }

    public List<OrderEntity> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    public List<OrderEntity> findByBuyerIdAndStatus(Long buyerId, OrderStatus status) {
        return orderRepository.findByBuyerIdAndStatus(buyerId, status);
    }

    public List<OrderEntity> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByCreatedAtBetween(startDate, endDate);
    }

    public boolean existsByOrderNumber(String orderNumber) {
        return orderRepository.existsByOrderNumber(orderNumber);
    }

    public Page<OrderEntity> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        orderRepository.deleteById(id);
    }

    // Order Item operations
    public OrderItemEntity saveOrderItem(OrderItemEntity item) {
        return orderItemRepository.save(item);
    }

    public List<OrderItemEntity> findItemsByOrderId(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    public List<OrderItemEntity> findItemsByProductId(Long productId) {
        return orderItemRepository.findByProductId(productId);
    }
}