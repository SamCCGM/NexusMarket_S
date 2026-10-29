package application.exception;

/**
 * Se lanza cuando se solicita un recurso que no existe
 * (por ejemplo, un usuario, vendedor o bodega inexistente).
 * Se traduce a HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
