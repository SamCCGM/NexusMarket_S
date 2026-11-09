package application.controller.returns;

import application.domain.dto.returns.ReturnRequest;
import application.domain.dto.returns.ReturnResponse;
import application.domain.service.BuyerService;
import application.domain.service.OrderService;
import application.domain.service.ReturnService;
import application.persistence.mysql.entity.OrderEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API de Return (SDD - Return Services): RequestReturn (Buyer) y
 * ApproveReturn (Administrator).
 */
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;
    private final BuyerService buyerService;
    private final OrderService orderService;

    /**
     * RequestReturn (SDD): el comprador solicita la devolucion de una linea
     * de un pedido entregado/finalizado propio.
     */
    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ReturnResponse> requestReturn(
            @RequestBody @Valid ReturnRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                returnService.requestReturn(authentication.getName(), request));
    }

    /**
     * ApproveReturn (SDD): aprueba la solicitud si cumple las condiciones.
     * La aprobacion no devuelve unidades al inventario automaticamente.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<ReturnResponse> approveReturn(
            @PathVariable Long id,
            @RequestParam(required = false) String resolutionNotes) {

        return ResponseEntity.ok(returnService.approveReturn(id, resolutionNotes));
    }

    /**
     * ReceiveReturnUnits (SDD): registra la recepcion y revision de las
     * unidades devueltas aptas (reincorporacion al inventario con trazabilidad).
     * damagedQuantity (opcional) declara unidades recibidas no aptas para venta.
     */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ReturnResponse> receiveReturnUnits(
            @PathVariable Long id,
            @RequestParam Long warehouseId,
            @RequestParam(required = false, defaultValue = "0") Integer damagedQuantity) {

        return ResponseEntity.ok(returnService.receiveReturnUnits(id, warehouseId, damagedQuantity));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ReturnResponse> getById(@PathVariable Long id, Authentication authentication) {
        ReturnResponse response = returnService.findById(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAdmin(authentication) && !isBuyerOwner(response.getBuyerId(), authentication)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<ReturnResponse>> getByOrder(@PathVariable Long orderId,
                                                           Authentication authentication) {
        OrderEntity order = orderService.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAdmin(authentication) && !isBuyerOwner(order, authentication)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(returnService.findByOrder(orderId).stream()
                .map(r -> toResponseSafe(r))
                .collect(Collectors.toList()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<ReturnResponse>> getByStatus(@RequestParam String status) {
        return ResponseEntity.ok(returnService.findByStatus(status).stream()
                .map(r -> toResponseSafe(r))
                .collect(Collectors.toList()));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));
    }

    private boolean isBuyerOwner(Long buyerId, Authentication authentication) {
        return authentication != null && buyerService.findByUserEntityEmail(authentication.getName())
                .map(buyer -> buyer.getId().equals(buyerId))
                .orElse(false);
    }

    private boolean isBuyerOwner(OrderEntity order, Authentication authentication) {
        return authentication != null
                && order.getBuyer() != null
                && authentication.getName().equals(order.getBuyer().getEmail());
    }

    private ReturnResponse toResponseSafe(application.persistence.mysql.entity.ReturnEntity r) {
        return ReturnResponse.builder()
                .id(r.getId())
                .returnNumber(r.getReturnNumber())
                .orderId(r.getOrder().getId())
                .orderNumber(r.getOrder().getOrderNumber())
                .orderItemId(r.getOrderItem().getId())
                .productName(r.getOrderItem().getProduct() != null ? r.getOrderItem().getProduct().getName() : null)
                .buyerId(r.getOrder().getBuyer().getId())
                .reason(r.getReason())
                .quantity(r.getQuantity())
                .refundAmount(r.getRefundAmount())
                .status(r.getStatus())
                .resolutionNotes(r.getResolutionNotes())
                .createdAt(r.getCreatedAt())
                .processedAt(r.getProcessedAt())
                .build();
    }
}
