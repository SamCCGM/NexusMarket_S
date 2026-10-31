package application.domain.dto.buyer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RegisterBuyer (SDD): datos para el alta de un comprador.
 */
@Data
public class BuyerRegisterRequest {
    @NotBlank
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank
    @Size(min = 5, max = 150)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    private String phoneNumber;
}
