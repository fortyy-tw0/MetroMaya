package appointment.repository;

import java.time.LocalDate;
import java.util.List;

/** People waiting for a place on a fully booked day ("Notify me if a place opens"). */
public interface WaitlistRepository {
    record Entry(String id, String officeId, LocalDate date, String email) {}

    /** Adds the email to that day's list; joining the same day twice is not an error. */
    void add(String officeId, LocalDate date, String email);

    /** Entries for that office and day that have not been emailed yet, oldest first. */
    List<Entry> waiting(String officeId, LocalDate date);

    /** How many days from {@code from} on this email is still waiting for. */
    int countWaiting(String email, LocalDate from);

    void markNotified(String id);
}
