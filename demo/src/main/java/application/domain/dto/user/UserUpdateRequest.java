package application.domain.dto.user;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String fullName;
    private String phoneNumber;
}
