package application.persistence.mongodb.repository;

import application.persistence.mongodb.document.ShipmentTrackingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentTrackingRepository extends MongoRepository<ShipmentTrackingDocument, String> {

    Optional<ShipmentTrackingDocument> findByTrackingNumber(String trackingNumber);

    List<ShipmentTrackingDocument> findByOrderId(Long orderId);

    List<ShipmentTrackingDocument> findByLogisticsOperatorId(Long logisticsOperatorId);

    List<ShipmentTrackingDocument> findByStatus(String status);
}