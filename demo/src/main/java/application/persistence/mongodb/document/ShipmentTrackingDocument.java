package application.persistence.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "shipment_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentTrackingDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String trackingNumber;

    @Indexed
    private Long orderId;

    @Indexed
    private Long logisticsOperatorId;

    private String carrier;
    private String status;
    private String originAddress;
    private String destinationAddress;

    private List<TrackingEvent> events = new ArrayList<>();

    private LocalDateTime estimatedDelivery;
    private LocalDateTime actualDelivery;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingEvent {
        private LocalDateTime timestamp;
        private String status;
        private String location;
        private String description;
        private Double latitude;
        private Double longitude;
    }
}