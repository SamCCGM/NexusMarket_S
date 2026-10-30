package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PhysicalProduct representa un producto fisico que requiere gestion de
 * inventario y envio; su disponibilidad depende de las existencias en bodega.
 */
@Getter
@Setter
@NoArgsConstructor
public class PhysicalProduct extends Product {

    /**
     * Los productos fisicos requieren envio fisico obligatoriamente.
     */
    public boolean requiresShipping() {
        return true;
    }

    /** Datos fisicos adicionales obligatorios antes de publicar. */
    public void assertRequiredData() {
        super.assertRequiredData();
    }
}
