package application.domain.dto.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private String phoneNumber;
}
