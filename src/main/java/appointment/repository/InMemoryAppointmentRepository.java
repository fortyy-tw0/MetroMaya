package appointment.repository;

import appointment.model.Appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps appointments in memory. Data is lost when the server restarts. */
public class InMemoryAppointmentRepository implements AppointmentRepository {

    private final Map<String, Appointment> byReference = new ConcurrentHashMap<>();

    @Override
    public void save(Appointment appointment) {
        byReference.put(appointment.getReferenceNo(), appointment);
    }

    @Override
    public Optional<Appointment> findByReference(String referenceNo) {
        if (referenceNo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byReference.get(referenceNo.trim().toUpperCase()));
    }

    @Override
    public long countActive(String officeId, LocalDate date, LocalTime time) {
        return byReference.values().stream()
                .filter(Appointment::isActive)
                .filter(a -> a.getOffice().getId().equals(officeId))
                .filter(a -> a.getDate().equals(date) && a.getTime().equals(time))
                .count();
    }

    @Override
    public List<Appointment> findActiveByEmail(String email) {
        return byReference.values().stream()
                .filter(Appointment::isActive)
                .filter(a -> a.getApplicant().getEmail().equalsIgnoreCase(email))
                .toList();
    }
}
