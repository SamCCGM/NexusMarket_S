package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LogisticsOperator extends User {

    /**
     * El operador logistico administra bodegas, envios y movimientos de
     * inventario; requiere una cuenta activa.
     */
    public void assertCanOperateLogistics() {
        assertIdentity();
        if (!canOperate()) {
            throw new application.exception.BusinessRuleException(
                    "Un operador logistico bloqueado no puede ejecutar operaciones logisticas");
        }
    }
}