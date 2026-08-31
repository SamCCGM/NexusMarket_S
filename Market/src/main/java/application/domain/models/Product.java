package application.domain.models;

import application.domain.enums.ProductStatus;
import application.domain.valueobjects.Variant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public abstract class Product {

    private int productId;
    private List<Variant> variants;
    private ProductStatus status;
    
}