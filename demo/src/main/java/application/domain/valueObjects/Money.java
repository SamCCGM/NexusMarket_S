package application.domain.valueObjects;

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

    /** Una cantidad monetaria no puede ser negativa. */
    public boolean isNonNegative() {
        return amount != null && amount.signum() >= 0;
    }

    public boolean isPositive() {
        return amount != null && amount.signum() > 0;
    }

    /** Suma solo valores de la misma moneda. */
    public Money add(Money other) {
        assertSameCurrency(other);
        return Money.builder()
                .amount(this.amount.add(other.amount))
                .currency(this.currency)
                .build();
    }

    public Money multiply(int factor) {
        return Money.builder()
                .amount(this.amount.multiply(BigDecimal.valueOf(factor)))
                .currency(this.currency)
                .build();
    }

    private void assertSameCurrency(Money other) {
        if (this.currency == null || other == null || !this.currency.equals(other.currency)) {
            throw new application.exception.BusinessRuleException(
                    "Las cantidades monetarias deben expresarse en la misma moneda");
        }
    }
}
