package appointment.service;

/** Thrown when a booking rule is broken. The message is safe to show to the citizen. */
public class BookingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BookingException(String message) {
        super(message);
    }
}
