package application.service;

import application.persistence.mongodb.document.ShipmentTrackingDocument;
import application.persistence.mongodb.repository.ShipmentTrackingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ShipmentTrackingService {

    @Autowired
    private ShipmentTrackingRepository trackingRepository;

    public ShipmentTrackingDocument createTracking(ShipmentTrackingDocument tracking) {
        tracking.setCreatedAt(LocalDateTime.now());
        tracking.setUpdatedAt(LocalDateTime.now());
        return trackingRepository.save(tracking);
    }

    public Optional<ShipmentTrackingDocument> findByTrackingNumber(String trackingNumber) {
        return trackingRepository.findByTrackingNumber(trackingNumber);
    }

    public List<ShipmentTrackingDocument> findByOrderId(Long orderId) {
        return trackingRepository.findByOrderId(orderId);
    }

    public List<ShipmentTrackingDocument> findByLogisticsOperatorId(Long logisticsOperatorId) {
        return trackingRepository.findByLogisticsOperatorId(logisticsOperatorId);
    }

    public List<ShipmentTrackingDocument> findByStatus(String status) {
        return trackingRepository.findByStatus(status);
    }

    public ShipmentTrackingDocument addTrackingEvent(String trackingNumber, ShipmentTrackingDocument.TrackingEvent event) {
        Optional<ShipmentTrackingDocument> trackingOpt = trackingRepository.findByTrackingNumber(trackingNumber);
        
        if (trackingOpt.isEmpty()) {
            throw new RuntimeException("Tracking not found for number: " + trackingNumber);
        }

        ShipmentTrackingDocument tracking = trackingOpt.get();
        event.setTimestamp(LocalDateTime.now());
        tracking.getEvents().add(event);
        tracking.setStatus(event.getStatus());
        tracking.setUpdatedAt(LocalDateTime.now());
        
        return trackingRepository.save(tracking);
    }

    public ShipmentTrackingDocument updateStatus(String trackingNumber, String status, String location, String description) {
        Optional<ShipmentTrackingDocument> trackingOpt = trackingRepository.findByTrackingNumber(trackingNumber);
        
        if (trackingOpt.isEmpty()) {
            throw new RuntimeException("Tracking not found for number: " + trackingNumber);
        }

        ShipmentTrackingDocument tracking = trackingOpt.get();
        ShipmentTrackingDocument.TrackingEvent event = new ShipmentTrackingDocument.TrackingEvent();
        event.setTimestamp(LocalDateTime.now());
        event.setStatus(status);
        event.setLocation(location);
        event.setDescription(description);
        
        tracking.getEvents().add(event);
        tracking.setStatus(status);
        tracking.setUpdatedAt(LocalDateTime.now());
        
        if ("DELIVERED".equalsIgnoreCase(status)) {
            tracking.setActualDelivery(LocalDateTime.now());
        }
        
        return trackingRepository.save(tracking);
    }

    public void deleteById(String id) {
        trackingRepository.deleteById(id);
    }
}