package application.domain.dto.seller;

import lombok.Data;

@Data
public class SellerUpdateRequest {
    private String companyName;
    private String taxId;
    private String description;
}
