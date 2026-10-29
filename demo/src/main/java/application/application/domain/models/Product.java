package application.domain.models;

import application.domain.enums.ProductStatus;
import application.domain.valueobjects.Variant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String characteristics;
    private List<Variant> variants;
    private ProductStatus status;

}
