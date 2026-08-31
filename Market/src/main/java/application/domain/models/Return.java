package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Return {

    private int returnId;
    private Order order;
    private String reason;

}