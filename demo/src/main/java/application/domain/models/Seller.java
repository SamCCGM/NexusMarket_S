package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Seller representa al usuario responsable de registrar y administrar los
 * productos que comercializa dentro del Marketplace.
 *
 * Los vendedores son incorporados administrativamente por un Administrator
 * y no pueden registrarse por sí mismos.
 *
 * Las relaciones con Product y Warehouse se representan mediante asociaciones
 * del dominio, por lo que no se almacenan listas dentro de Seller.
 */
@Getter
@Setter
@NoArgsConstructor
public class Seller extends User {

}
