package application.domain.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * GenerateAdministrativeReport (SDD - Reporting Services).
 * Informacion de consulta y seguimiento administrativo del Marketplace.
 * Solo lectura: no modifica las entidades consultadas.
 */
@Data
@Builder
public class AdminReportResponse {
    private LocalDateTime generatedAt;

    // Usuarios
    private Long totalUsers;
    private Long activeUsers;
    private Long blockedUsers;
    private Long totalBuyers;
    private Long totalSellers;
    private Long totalWarehouses;

    // Productos
    private Long totalProducts;
    private Map<String, Long> productsByStatus;

    // Pedidos
    private Long totalOrders;
    private Map<String, Long> ordersByStatus;
    private Long paidOrders;
    private BigDecimal totalRevenue;

    // Envios
    private Map<String, Long> shipmentsByStatus;
    private Long pendingShipments;

    // Devoluciones y reembolsos
    private Long pendingReturns;
    private Long approvedReturns;
    private Long completedRefunds;
    private BigDecimal totalRefunded;

    // Inventario
    private Long lowStockItems;
    private Long totalInventoryRecords;
}
