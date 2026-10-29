package application.domain.dto.seller;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SellerResponse {
    private Long id;
    private String fullName;
    private String email;
    private String companyName;
    private String taxId;
    private String description;
    private Double rating;
    private Long totalSales;
    private String status;
}
