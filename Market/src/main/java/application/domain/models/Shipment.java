package application.domain.models;

import application.domain.valueobjects.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    private Integer shipmentId;
    private Order order;
    private Address destination;
}