package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DigitalProduct representa un producto intangible entregado digitalmente.
 * No requiere envio fisico ni inventario: su entrega ocurre despues de
 * confirmar el pago.
 */
@Getter
@Setter
@NoArgsConstructor
public class DigitalProduct extends Product {

    private String downloadUrl;
    private Integer maxDownloads;
    private boolean licenseKeyRequired;

    /** El archivo debe tener una direccion descargable cuando aplica. */
    public void assertDigitalData() {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new application.exception.BusinessRuleException(
                    "El producto debe definir la direccion de descarga de su archivo");
        }
        if (maxDownloads != null && maxDownloads <= 0) {
            throw new application.exception.BusinessRuleException(
                    "El maximo de descargas debe ser positivo (o -1 para ilimitado)");
        }
    }

    public boolean requiresShipping() {
        return false;
    }
}
