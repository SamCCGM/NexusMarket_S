package application.domain.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @Size(min = 2, max = 100)
    private String fullName;

    @Size(max = 20)
    private String phoneNumber;
}
