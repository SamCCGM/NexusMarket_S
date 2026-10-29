package application.domain.valueobjects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Money (Value Object).
 * Representa un valor monetario utilizado para expresar cantidades
 * económicas dentro de las operaciones comerciales del Marketplace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Money {

    private BigDecimal amount;
    private String currency;

}
