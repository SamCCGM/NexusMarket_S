package application.domain.dto.buyer;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BuyerUpdateRequest {
    @Size(min = 2, max = 100)
    private String fullName;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 255)
    private String street;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 20)
    private String mainPhoneNumber;
}
