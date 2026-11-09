package application.persistence.mysql.repository;

import application.persistence.mysql.entity.OrderEntity;
import application.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Las consultas que se exponen por la API cargan comprador, items, producto y
 * variante para evitar LazyInitializationException con open-in-view=false.
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    List<OrderEntity> findByBuyerId(Long buyerId);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    List<OrderEntity> findByStatus(OrderStatus status);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    Page<OrderEntity> findByBuyerId(Long buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant", "items.product.seller"})
    @Query("SELECT DISTINCT o FROM OrderEntity o JOIN o.items i JOIN i.product p JOIN p.seller s "
            + "WHERE o.buyer.id = :buyerId AND s.id = :sellerId")
    Page<OrderEntity> findByBuyerIdAndSellerId(@Param("buyerId") Long buyerId,
                                               @Param("sellerId") Long sellerId,
                                               Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    Page<OrderEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    List<OrderEntity> findByBuyerIdAndStatus(Long buyerId, OrderStatus status);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    Optional<OrderEntity> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant"})
    @Query("SELECT o FROM OrderEntity o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    List<OrderEntity> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    List<OrderEntity> findByStatusAndDeliveredAtBefore(OrderStatus status, LocalDateTime cutoff);

    List<OrderEntity> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"buyer", "items", "items.product", "items.variant", "items.product.seller"})
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithDetails(@Param("id") Long id);
}
