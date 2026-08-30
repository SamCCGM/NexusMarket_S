package application.domain.models;

import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class User {

    private Integer userId;
    private String fullName;
    private String email;
    private UserRole role;
    private UserStatus status;
}