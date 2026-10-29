package application.exception;

/**
 * Se lanza cuando una operación viola una regla de negocio del dominio
 * (correo ya registrado, transición de estado inválida, datos requeridos
 * ausentes, etc.). Se traduce a HTTP 409.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
