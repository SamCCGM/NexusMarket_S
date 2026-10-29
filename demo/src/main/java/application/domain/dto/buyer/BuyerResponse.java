package application.domain.dto.buyer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuyerResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String status;
    private String buyerStatus;
    private String street;
    private String city;
    private String state;
    private String country;
    private String postalCode;
}
