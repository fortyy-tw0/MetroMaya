package appointment.service;

/** Thrown when a service, office or appointment does not exist. */
public class NotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
