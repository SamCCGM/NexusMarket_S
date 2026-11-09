package application.domain.dto.seller;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SellerUpdateRequest {
    @Size(min = 2, max = 150)
    private String companyName;

    @Size(max = 50)
    private String taxId;

    @Size(max = 1000)
    private String description;
}
