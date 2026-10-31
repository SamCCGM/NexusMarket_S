package application.domain.dto.shipment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ShipmentResponse {
    private Long id;
    private String trackingNumber;
    private Long externalShipmentId;
    private Long orderId;
    private String orderNumber;
    private String orderStatus;
    private Long logisticsOperatorId;
    private Long warehouseId;
    private String carrier;
    private String status;
    private String destinationStreet;
    private String destinationCity;
    private String destinationState;
    private String destinationCountry;
    private String destinationPostalCode;
    private BigDecimal shippingCost;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private LocalDateTime createdAt;
}
