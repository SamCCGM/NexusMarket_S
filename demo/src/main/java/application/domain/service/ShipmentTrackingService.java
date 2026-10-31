package application.domain.service;

import application.domain.ports.NotificationPort;
import application.persistence.mongodb.document.ShipmentTrackingDocument;
import application.persistence.mongodb.repository.ShipmentTrackingRepository;
import application.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de rastreo de envios (SDD - TrackShipment / UpdateShipmentStatus).
 * Cada evento de rastreo notifica al comprador via NotificationPort.
 */
@Service
public class ShipmentTrackingService {

    @Autowired
    private ShipmentTrackingRepository trackingRepository;

    @Autowired
    private NotificationPort notificationPort;

    public ShipmentTrackingDocument createTracking(ShipmentTrackingDocument tracking) {
        tracking.setCreatedAt(LocalDateTime.now());
        tracking.setUpdatedAt(LocalDateTime.now());
        ShipmentTrackingDocument saved = trackingRepository.save(tracking);
        notificationPort.notifyOrderEvent(saved.getOrderId(),
                "Seguimiento activado para el envio " + saved.getTrackingNumber());
        return saved;
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
            throw new ResourceNotFoundException("Tracking not found for number: " + trackingNumber);
        }

        ShipmentTrackingDocument tracking = trackingOpt.get();
        event.setTimestamp(LocalDateTime.now());
        tracking.getEvents().add(event);
        tracking.setStatus(event.getStatus());
        tracking.setUpdatedAt(LocalDateTime.now());

        ShipmentTrackingDocument saved = trackingRepository.save(tracking);
        notificationPort.notifyOrderEvent(saved.getOrderId(),
                "Rastreo " + saved.getTrackingNumber() + ": " + event.getStatus()
                        + (event.getLocation() != null ? " (" + event.getLocation() + ")" : ""));
        return saved;
    }

    public ShipmentTrackingDocument updateStatus(String trackingNumber, String status, String location, String description) {
        Optional<ShipmentTrackingDocument> trackingOpt = trackingRepository.findByTrackingNumber(trackingNumber);

        if (trackingOpt.isEmpty()) {
            throw new ResourceNotFoundException("Tracking not found for number: " + trackingNumber);
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

        ShipmentTrackingDocument saved = trackingRepository.save(tracking);
        notificationPort.notifyOrderEvent(saved.getOrderId(),
                "El envio " + saved.getTrackingNumber() + " cambio de estado: " + status);
        return saved;
    }

    public void deleteById(String id) {
        trackingRepository.deleteById(id);
    }
}