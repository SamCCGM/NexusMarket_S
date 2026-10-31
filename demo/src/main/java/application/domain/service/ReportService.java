package application.domain.service;

import application.domain.dto.report.AdminReportResponse;
import application.persistence.mongodb.document.InventoryDocument;
import application.persistence.mysql.entity.*;
import application.persistence.mysql.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GenerateAdministrativeReport (Actor: Administrator).
 * Genera informacion de consulta y seguimiento administrativo del registro.
 *
 * Reglas:
 * - Solo el rol autorizado genera reportes en esta version (Administrator).
 * - La informacion corresponde a los datos registrados por el sistema.
 * - El reporte es de solo lectura: no modifica entidades.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private InventoryMongoService inventoryMongoService;

    public AdminReportResponse generateAdministrativeReport() {
        List<UserEntity> users = userRepository.findAll();
        List<ProductEntity> products = productRepository.findAll();
        List<OrderEntity> orders = orderRepository.findAll();
        List<ShipmentEntity> shipments = shipmentRepository.findAll();
        List<ReturnEntity> returns = returnRepository.findAll();
        List<RefundEntity> refunds = refundRepository.findAll();
        List<InventoryDocument> inventory = inventoryMongoService.findByAll();

        long activeUsers = users.stream()
                .filter(u -> u.getStatus() == application.domain.enums.UserStatus.ACTIVE).count();
        long blockedUsers = users.size() - activeUsers;
        long buyers = users.stream()
                .filter(u -> u instanceof BuyerEntity).count();
        long sellers = users.stream()
                .filter(u -> u instanceof SellerEntity).count();

        Map<String, Long> productsByStatus = groupCount(products, p ->
                p.getStatus() != null ? p.getStatus().name() : "UNKNOWN");
        Map<String, Long> ordersByStatus = groupCount(orders, o ->
                o.getStatus() != null ? o.getStatus().name() : "UNKNOWN");
        Map<String, Long> shipmentsByStatus = groupCount(shipments, s ->
                s.getStatus() != null ? s.getStatus() : "UNKNOWN");

        long paidOrders = orders.stream()
                .filter(o -> o.getPaidAt() != null).count();
        long cancelledOrders = orders.stream()
                .filter(o -> o.getStatus() == application.domain.enums.OrderStatus.CANCELLED).count();
        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getPaidAt() != null && o.getTotalAmount() != null)
                .map(OrderEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingShipments = shipments.stream()
                .filter(s -> !"DELIVERED".equals(s.getStatus())).count();

        BigDecimal totalRefunded = refunds.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()) && r.getAmount() != null)
                .map(RefundEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long lowStock = inventory.stream().filter(InventoryDocument::isLowStock).count();
        long damagedStockItems = inventory.stream()
                .filter(i -> i.getDamagedQuantity() > 0).count();
        long totalDamagedUnits = inventory.stream()
                .mapToLong(InventoryDocument::getDamagedQuantity).sum();

        return AdminReportResponse.builder()
                .generatedAt(LocalDateTime.now())
                .totalUsers((long) users.size())
                .activeUsers(activeUsers)
                .blockedUsers(blockedUsers)
                .totalBuyers(buyers)
                .totalSellers(sellers)
                .totalWarehouses(warehouseRepository.count())
                .totalProducts((long) products.size())
                .productsByStatus(productsByStatus)
                .totalOrders((long) orders.size())
                .ordersByStatus(ordersByStatus)
                .paidOrders(paidOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .shipmentsByStatus(shipmentsByStatus)
                .pendingShipments(pendingShipments)
                .pendingReturns(returns.stream()
                        .filter(r -> "REQUESTED".equals(r.getStatus())).count())
                .approvedReturns(returns.stream()
                        .filter(r -> "APPROVED".equals(r.getStatus())).count())
                .receivedReturns(returns.stream()
                        .filter(r -> "RECEIVED".equals(r.getStatus())).count())
                .completedRefunds(refunds.stream()
                        .filter(r -> "COMPLETED".equals(r.getStatus())).count())
                .totalRefunded(totalRefunded)
                .lowStockItems(lowStock)
                .totalInventoryRecords((long) inventory.size())
                .damagedStockItems(damagedStockItems)
                .totalDamagedUnits(totalDamagedUnits)
                .build();
    }

    private <T> Map<String, Long> groupCount(List<T> items, Function<T, String> key) {
        return items.stream()
                .collect(Collectors.groupingBy(key, Collectors.counting()));
    }
}
