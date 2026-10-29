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

}
