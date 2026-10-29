package application.controller.user;

import application.domain.dto.user.UserResponse;
import application.domain.dto.user.UserUpdateRequest;
import application.domain.enums.UserRole;
import application.domain.service.UserService;
import application.persistence.mysql.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {

        List<UserEntity> users;
        if (role != null) {
            users = userService.findByRole(role);
        } else if (status != null) {
            users = userService.findByStatus(status);
        } else {
            users = userService.findAll();
        }
        return ResponseEntity.ok(users.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * UpdateUser (SDD): actualiza la informacion general. El rol y el estado
     * no se modifican aqui (el estado se gestiona con Block/Activate).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateRequest request) {

        UserEntity updates = new UserEntity() {};
        updates.setFullName(request.getFullName());
        updates.setPhoneNumber(request.getPhoneNumber());
        return ResponseEntity.ok(toResponse(userService.updateUser(id, updates)));
    }

    /**
     * BlockUser (SDD): impide que el usuario participe en operaciones que
     * requieran cuenta activa, sin borrar su historial.
     */
    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponse> blockUser(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(userService.blockUser(id)));
    }

    /**
     * ActivateUser (SDD): reactiva a un usuario previamente bloqueado.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(userService.activateUser(id)));
    }

    private UserResponse toResponse(UserEntity entity) {
        return UserResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .role(entity.getRole() != null ? entity.getRole().name() : null)
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .phoneNumber(entity.getPhoneNumber())
                .build();
    }
}
