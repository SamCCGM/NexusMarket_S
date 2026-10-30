package application.domain.models;

import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User representa a cualquier participante autorizado que interactúa con
 * el sistema NexusMarket.
 *
 * User es una clase abstracta porque representa un concepto general de
 * usuario. No debe instanciarse directamente; cada usuario pertenece a
 * una única especialización (Buyer, Seller, Administrator, LogisticsOperator)
 * y tiene un único rol dentro del sistema.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class User {

    private int userId;
    private String name;
    private String email;
    private UserRole role;
    private UserStatus status;

    /**
     * Un operador solo puede participar en operaciones del sistema con una
     * cuenta activa (BlockUser impide esa participacion sin borrar historial).
     */
    public boolean canOperate() {
        return status == UserStatus.ACTIVE;
    }

    /** Cada usuario pertenece a un unico rol y se identifica con un correo unico. */
    public void assertIdentity() {
        if (name == null || name.isBlank()) {
            throw new application.exception.BusinessRuleException("El nombre del usuario es obligatorio");
        }
        if (email == null || email.isBlank()) {
            throw new application.exception.BusinessRuleException("El correo del usuario es obligatorio");
        }
        if (role == null) {
            throw new application.exception.BusinessRuleException("El usuario debe tener un unico rol asignado");
        }
    }

    /** BlockUser: bloquea la cuenta sin borrar el historial. */
    public void block() {
        if (status == UserStatus.BLOCKED) {
            throw new application.exception.BusinessRuleException("El usuario ya se encuentra bloqueado");
        }
        status = UserStatus.BLOCKED;
    }

    /** ActivateUser: reactiva una cuenta previamente bloqueada. */
    public void activate() {
        if (status == UserStatus.ACTIVE) {
            throw new application.exception.BusinessRuleException("El usuario ya se encuentra activo");
        }
        status = UserStatus.ACTIVE;
    }
}
