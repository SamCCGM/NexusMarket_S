package application.domain.models;

import application.domain.enums.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Order {

    private int orderId;
    private Buyer buyer;
    private List<OrderItem> items = new ArrayList<>();
    private OrderStatus status;
}