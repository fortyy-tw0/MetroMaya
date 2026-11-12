package appointment.model;

/** One line of a fee breakdown, such as "Fire safety inspection: 500.00". */
public record FeeLine(String label, double amount) {
}
