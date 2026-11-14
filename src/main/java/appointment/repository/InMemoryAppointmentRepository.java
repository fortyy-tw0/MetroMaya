package appointment.repository;

import appointment.model.Appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
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
    public Optional<Appointment> findByCheckInCode(String checkInCode) {
        if (checkInCode == null || checkInCode.isBlank()) return Optional.empty();
        return byReference.values().stream()
                .filter(a -> checkInCode.equals(a.getCheckInCode())).findFirst();
    }

    @Override
    public List<Appointment> listByOfficeRange(String officeId, LocalDate from, LocalDate to) {
        return byReference.values().stream()
                .filter(a -> a.getOffice().getId().equals(officeId))
                .filter(a -> !a.getDate().isBefore(from) && !a.getDate().isAfter(to))
                .sorted(Comparator.comparing(Appointment::getDate).thenComparing(Appointment::getTime)
                        .thenComparing(Appointment::getReferenceNo)).toList();
    }

    @Override
    public boolean officialReceiptExists(String officeId, String officialReceiptNo) {
        return byReference.values().stream().anyMatch(a -> a.getOffice().getId().equals(officeId)
                && a.getCounterPayment() != null
                && a.getCounterPayment().officialReceiptNo().equalsIgnoreCase(officialReceiptNo));
    }

    @Override
    public long countActive(String officeId, LocalDate date, LocalTime time) {
        return byReference.values().stream()
                .filter(Appointment::isActive)
                .filter(a -> !a.isWalkIn())
                .filter(a -> a.getOffice().getId().equals(officeId))
                .filter(a -> a.getDate().equals(date) && a.getTime().equals(time))
                .count();
    }

    @Override
    public List<Appointment> findActiveByEmail(String email) {
        return byReference.values().stream()
                .filter(Appointment::isActive)
                .filter(a -> !a.isWalkIn())
                .filter(a -> a.getApplicant().getEmail().equalsIgnoreCase(email))
                .toList();
    }

    @Override
    public long countActiveByVerifiedId(String verifiedId) {
        return byReference.values().stream()
                .filter(Appointment::isActive)
                .filter(a -> !a.isWalkIn())
                .filter(a -> verifiedId.equals(a.getApplicant().getVerifiedId()))
                .count();
    }
}
