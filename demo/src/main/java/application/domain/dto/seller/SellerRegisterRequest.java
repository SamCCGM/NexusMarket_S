package application.domain.dto.seller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RegisterSeller (SDD): datos para incorporar un vendedor.
 * Los vendedores son incorporados administrativamente por un Administrator.
 */
@Data
public class SellerRegisterRequest {
    @NotBlank
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    /** Nombre de la compania: obligatorio para vender en el Marketplace. */
    @NotBlank
    @Size(min = 2, max = 150)
    private String companyName;

    @Size(max = 50)
    private String taxId;

    @Size(max = 1000)
    private String description;
}
