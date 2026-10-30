package application.controller.shipment;

import application.domain.dto.shipment.ShipmentCreateRequest;
import application.domain.dto.shipment.ShipmentResponse;
import application.domain.service.OrderService;
import application.persistence.mysql.entity.ShipmentEntity;
import application.persistence.mysql.repository.ShipmentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API de Shipment (SDD - Shipment Services).
 * DispatchShipment y ConfirmDelivery se exponen en OrderController porque
 * coordinan el estado del envio y del pedido; aqui se expone CreateShipment.
 */
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final OrderService orderService;
    private final ShipmentRepository shipmentRepository;

    /**
     * CreateShipment (SDD): crea un envio para un pedido fisico pagado.
     */
    @PostMapping("/order/{orderId}")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ShipmentResponse> createShipment(
            @PathVariable Long orderId,
            @RequestBody(required = false) @Valid ShipmentCreateRequest request) {

        ShipmentEntity saved = orderService.createShipment(
                orderId,
                request != null ? request.getLogisticsOperatorId() : null,
                request != null ? request.getWarehouseId() : null,
                request != null ? request.getCarrier() : null,
                request != null ? request.getEstimatedDays() : null);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR') or hasRole('BUYER')")
    public ResponseEntity<List<ShipmentResponse>> getByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(shipmentRepository.findByOrderId(orderId).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ShipmentResponse> getById(@PathVariable Long id) {
        return shipmentRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<ShipmentResponse>> getByStatus(@RequestParam(required = false) String status) {
        List<ShipmentEntity> shipments = status != null
                ? shipmentRepository.findByStatus(status)
                : shipmentRepository.findAll();
        return ResponseEntity.ok(shipments.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    private ShipmentResponse toResponse(ShipmentEntity entity) {
        return ShipmentResponse.builder()
                .id(entity.getId())
                .trackingNumber(entity.getTrackingNumber())
                .orderId(entity.getOrder().getId())
                .orderNumber(entity.getOrder().getOrderNumber())
                .orderStatus(entity.getOrder().getStatus() != null ? entity.getOrder().getStatus().name() : null)
                .logisticsOperatorId(entity.getLogisticsOperator() != null ? entity.getLogisticsOperator().getId() : null)
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .carrier(entity.getCarrier())
                .status(entity.getStatus())
                .destinationStreet(entity.getDestinationAddress() != null ? entity.getDestinationAddress().getStreet() : null)
                .destinationCity(entity.getDestinationAddress() != null ? entity.getDestinationAddress().getCity() : null)
                .destinationState(entity.getDestinationAddress() != null ? entity.getDestinationAddress().getState() : null)
                .destinationCountry(entity.getDestinationAddress() != null ? entity.getDestinationAddress().getCountry() : null)
                .destinationPostalCode(entity.getDestinationAddress() != null ? entity.getDestinationAddress().getPostalCode() : null)
                .shippingCost(entity.getShippingCost())
                .estimatedDeliveryDate(entity.getEstimatedDeliveryDate())
                .actualDeliveryDate(entity.getActualDeliveryDate())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
