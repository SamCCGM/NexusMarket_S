package application.persistence.mysql.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "physical_products")
@PrimaryKeyJoinColumn(name = "id")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PhysicalProductEntity extends ProductEntity {

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_cm")
    private Double lengthCm;

    @Column(name = "width_cm")
    private Double widthCm;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "requires_shipping")
    private boolean requiresShipping = true;

    @Column(name = "is_fragile")
    private boolean fragile = false;
}