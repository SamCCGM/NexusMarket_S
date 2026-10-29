package application.domain.dto.buyer;

import lombok.Data;

@Data
public class BuyerUpdateRequest {
    private String fullName;
    private String phoneNumber;
    private String street;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String mainPhoneNumber;
}
