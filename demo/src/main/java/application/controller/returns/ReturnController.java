package application.controller.returns;

import application.domain.dto.returns.ReturnRequest;
import application.domain.dto.returns.ReturnResponse;
import application.domain.service.ReturnService;
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

    /**
     * RequestReturn (SDD): el comprador solicita la devolucion de una linea
     * de un pedido entregado/finalizado propio.
     */
    @PostMapping
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ReturnResponse> requestReturn(
            @RequestBody ReturnRequest request,
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

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BUYER') or hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ReturnResponse> getById(@PathVariable Long id) {
        ReturnResponse response = returnService.findById(id);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<ReturnResponse>> getByOrder(@PathVariable Long orderId) {
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
