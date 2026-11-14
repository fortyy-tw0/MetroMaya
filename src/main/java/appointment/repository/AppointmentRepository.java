package appointment.repository;

import appointment.model.Appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Storage contract. Swap the in-memory version for a database one without touching the service layer. */
public interface AppointmentRepository {

    void save(Appointment appointment);

    Optional<Appointment> findByReference(String referenceNo);

    /** Active (non-cancelled) appointments for an office at an exact date and time. */
    long countActive(String officeId, LocalDate date, LocalTime time);

    /** Active appointments belonging to the given email address. */
    List<Appointment> findActiveByEmail(String email);

    /**
     * Active appointments for an office on a day, counted per time slot. The default asks
     * {@link #countActive} once per slot; a database repository can answer in one query.
     */
    default Map<LocalTime, Long> countActiveByTime(String officeId, LocalDate date, List<LocalTime> times) {
        Map<LocalTime, Long> counts = new HashMap<>();
        for (LocalTime time : times) {
            counts.put(time, countActive(officeId, date, time));
        }
        return counts;
    }
}
