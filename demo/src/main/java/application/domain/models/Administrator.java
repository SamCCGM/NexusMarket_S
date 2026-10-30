package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Administrator extends User {

    /**
     * El administrador aprueba devoluciones y genera reportes.
     * Debe tener una cuenta activa para actuar.
     */
    public void assertCanActOnResponsibilities() {
        assertIdentity();
        if (!canOperate()) {
            throw new application.exception.BusinessRuleException(
                    "Un administrador bloqueado no puede ejecutar sus responsabilidades");
        }
    }
}