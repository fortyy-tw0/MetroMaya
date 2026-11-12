package appointment.service;

import appointment.model.Appointment;
import appointment.model.Citizen;
import appointment.model.GovService;
import appointment.model.Office;
import appointment.repository.AppointmentRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All booking rules live here:
 * weekdays only, within the next 30 days, fixed hourly slots, per-slot capacity,
 * minimum age per service, and one active appointment per person per service.
 */
public class AppointmentService {

    public static final int MAX_DAYS_AHEAD = 30;

    /** Hourly slots, 8:00 to 16:00, with a lunch break at 12:00. */
    public static final List<LocalTime> SLOT_TIMES = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppointmentRepository repository;
    private final CatalogService catalog;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AppointmentService(AppointmentRepository repository, CatalogService catalog, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
    }

    public CatalogService getCatalog() {
        return catalog;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<SlotInfo> getSlots(String officeId, LocalDate date) {
        Office office = catalog.getOffice(officeId);
        validateDate(date);
        List<SlotInfo> slots = new ArrayList<>();
        for (LocalTime time : SLOT_TIMES) {
            int taken = (int) repository.countActive(office.getId(), date, time);
            int remaining = Math.max(0, office.getCapacityPerSlot() - taken);
            boolean inFuture = !LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock));
            slots.add(new SlotInfo(time, office.getCapacityPerSlot(), remaining, inFuture && remaining > 0));
        }
        return slots;
    }

    public synchronized Appointment book(String serviceId, String officeId,
                                         LocalDate date, LocalTime time, Citizen citizen) {
        GovService service = catalog.getService(serviceId);
        Office office = catalog.getOffice(officeId);

        if (!office.offers(service.getId())) {
            throw new BookingException(office.getName() + " does not offer " + service.getName() + ".");
        }
        validateSlot(office, date, time);

        if (citizen.ageOn(date) < service.getMinimumAge()) {
            throw new BookingException("You must be at least " + service.getMinimumAge()
                    + " years old for " + service.getName() + ".");
        }
        boolean alreadyBooked = repository.findActiveByEmail(citizen.getEmail()).stream()
                .anyMatch(a -> a.getService().getId().equals(service.getId()));
        if (alreadyBooked) {
            throw new BookingException("You already have an active appointment for "
                    + service.getName() + ". Cancel it first to book a new one.");
        }

        Appointment appointment = new Appointment(newReference(date), citizen, service, office,
                date, time, Instant.now(clock));
        repository.save(appointment);
        return appointment;
    }

    public Appointment find(String referenceNo) {
        return repository.findByReference(referenceNo)
                .orElseThrow(() -> new NotFoundException("No appointment found for that reference number."));
    }

    public synchronized Appointment cancel(String referenceNo) {
        Appointment appointment = find(referenceNo);
        appointment.cancel();
        repository.save(appointment);
        return appointment;
    }

    public synchronized Appointment reschedule(String referenceNo, LocalDate date, LocalTime time) {
        Appointment appointment = find(referenceNo);
        if (!appointment.isActive()) {
            throw new BookingException("A cancelled appointment cannot be rescheduled.");
        }
        if (date.equals(appointment.getDate()) && time.equals(appointment.getTime())) {
            throw new BookingException("Please choose a different date or time.");
        }
        validateSlot(appointment.getOffice(), date, time);
        appointment.moveTo(date, time);
        repository.save(appointment);
        return appointment;
    }

    // ---- rules ----

    private void validateDate(LocalDate date) {
        if (date == null) {
            throw new BookingException("Please choose a date.");
        }
        LocalDate today = today();
        if (date.isBefore(today)) {
            throw new BookingException("That date has already passed.");
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new BookingException("Appointments open up to " + MAX_DAYS_AHEAD + " days in advance.");
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new BookingException("Offices are closed on weekends. Please choose a weekday.");
        }
    }

    private void validateSlot(Office office, LocalDate date, LocalTime time) {
        validateDate(date);
        if (time == null || !SLOT_TIMES.contains(time)) {
            throw new BookingException("Please choose one of the available time slots.");
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))) {
            throw new BookingException("That time has already passed.");
        }
        long taken = repository.countActive(office.getId(), date, time);
        if (taken >= office.getCapacityPerSlot()) {
            throw new BookingException("That time slot is full. Please pick another.");
        }
    }

    private String newReference(LocalDate date) {
        String ref;
        do {
            StringBuilder sb = new StringBuilder("APT-")
                    .append(date.toString().replace("-", "")).append('-');
            for (int i = 0; i < 5; i++) {
                sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
            }
            ref = sb.toString();
        } while (repository.findByReference(ref).isPresent());
        return ref;
    }
}
