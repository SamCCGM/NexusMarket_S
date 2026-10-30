package application.domain.models;

import application.domain.valueObjects.Money;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del Value Object Money: invariantes monetarias del Marketplace.
 */
class MoneyTest {

    @Test
    @DisplayName("positivo / no negativo / negativo")
    void positivity() {
        assertTrue(money("10.00").isPositive());
        assertFalse(money("0").isPositive());
        assertTrue(money("0").isNonNegative());
        assertFalse(new Money(new BigDecimal("-5"), "USD").isPositive());
        assertFalse(new Money(new BigDecimal("-5"), "USD").isNonNegative());
    }

    @Test
    @DisplayName("valores nulos no son positivos")
    void nullAmount() {
        assertFalse(new Money(null, "USD").isPositive());
        assertFalse(new Money(null, "USD").isNonNegative());
    }

    @Test
    @DisplayName("add suma y conserva la moneda")
    void add() {
        Money result = money("10.50").add(money("2.25"));
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("12.75")));
        assertEquals("USD", result.getCurrency());
    }

    @Test
    @DisplayName("add rechaza monedas distintas (importe siempre en la misma moneda)")
    void addDifferentCurrencyThrows() {
        Money eur = new Money(new BigDecimal("1"), "EUR");
        assertThrows(BusinessRuleException.class, () -> money("10").add(eur));
    }

    @Test
    @DisplayName("multiply calcula el importe de una linea")
    void multiply() {
        Money result = money("4.10").multiply(3);
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("12.30")));
        assertEquals("USD", result.getCurrency());
    }

    private Money money(String value) {
        return new Money(new BigDecimal(value), "USD");
    }
}
