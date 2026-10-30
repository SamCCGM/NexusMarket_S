package application.domain.models;

import application.domain.enums.ProductStatus;
import application.domain.valueObjects.Money;
import application.domain.valueObjects.Variant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Product representa un bien ofrecido dentro del Marketplace.
 *
 * Product es una clase abstracta porque representa el concepto general de
 * producto. Sus especializaciones corresponden a las diferentes modalidades
 * ofrecidas: PhysicalProduct y DigitalProduct.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class Product {

    private int productId;
    private String name;
    private String description;
    private Money basePrice;
    private String category;
    private String sku;
    private String characteristics;
    private List<Variant> variants = new ArrayList<>();
    private ProductStatus status;

    /**
     * Registra los datos obligatorios del producto. Cada especializacion
     * agrega las validaciones propias de su tipo.
     */
    public void assertRequiredData() {
        if (name == null || name.isBlank()) {
            throw new application.exception.BusinessRuleException("El nombre del producto es obligatorio");
        }
        if (description == null || description.isBlank()) {
            throw new application.exception.BusinessRuleException("La descripcion del producto es obligatoria");
        }
        if (basePrice == null || !basePrice.isPositive()) {
            throw new application.exception.BusinessRuleException("El precio base del producto debe ser mayor que cero");
        }
        if (category == null || category.isBlank()) {
            throw new application.exception.BusinessRuleException("La categoria del producto es obligatoria");
        }
        if (sku == null || sku.isBlank()) {
            throw new application.exception.BusinessRuleException("El SKU del producto es obligatorio");
        }
        variants.forEach(Variant::assertValid);
    }

    /** Un producto suspendido o descontinuado no puede incluirse en nuevas compras. */
    public boolean canBePurchased() {
        return status == ProductStatus.PUBLISHED;
    }

    /**
     * Publicar un producto no garantiza disponibilidad: para productos fisicos
     * la disponibilidad depende del inventario, no del estado de publicacion.
     */
    public void publish() {
        if (status == ProductStatus.DISCONTINUED) {
            throw new application.exception.BusinessRuleException("Un producto descontinuado no puede publicarse");
        }
        if (status == ProductStatus.PUBLISHED) {
            throw new application.exception.BusinessRuleException("El producto ya se encuentra publicado");
        }
        assertRequiredData();
        status = ProductStatus.PUBLISHED;
    }

    public void suspend() {
        if (status != ProductStatus.PUBLISHED) {
            throw new application.exception.BusinessRuleException("Solo un producto publicado puede suspenderse");
        }
        status = ProductStatus.SUSPENDED;
    }

    public void discontinue() {
        if (status == ProductStatus.DISCONTINUED) {
            throw new application.exception.BusinessRuleException("El producto ya se encuentra descontinuado");
        }
        status = ProductStatus.DISCONTINUED;
    }
}
