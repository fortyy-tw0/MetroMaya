package appointment.service;

import appointment.model.Applicant;
import appointment.model.Appointment;
import appointment.model.Business;
import appointment.model.Office;
import appointment.model.PermitService;
import appointment.model.Quote;
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
import java.util.Map;

/**
 * All booking rules live here:
 * weekdays that aren't regular holidays, up to 3 months ahead, fixed hourly slots, per-slot capacity,
 * offices that only offer some services and serve some business sizes,
 * and one active booking per business per service.
 */
public class AppointmentService {

    /** Bookings open up to this many months ahead. */
    public static final int MAX_MONTHS_AHEAD = 3;

    /** Hourly slots, 8:00 to 16:00, with a lunch break at 12:00. */
    public static final List<LocalTime> SLOT_TIMES = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppointmentRepository repository;
    private final CatalogService catalog;
    private final Clock clock;
    private final HolidayCalendar holidays;
    private final SecureRandom random = new SecureRandom();

    public AppointmentService(AppointmentRepository repository, CatalogService catalog, Clock clock,
                              HolidayCalendar holidays) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
        this.holidays = holidays;
    }

    public CatalogService getCatalog() {
        return catalog;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The last day that can be booked: 3 months from today. */
    public LocalDate lastBookableDate() {
        return today().plusMonths(MAX_MONTHS_AHEAD);
    }

    /** Regular holidays from today to the last bookable day, when offices are closed. */
    public List<HolidayCalendar.Holiday> upcomingHolidays() {
        return holidays.between(today(), lastBookableDate());
    }

    /** Prices a service for a type of business and head count, without booking anything. */
    public Quote quote(String serviceId, String industryId, int employees) {
        PermitService service = catalog.getService(serviceId);
        return service.quote(Business.forEstimate(catalog.getIndustry(industryId), employees));
    }

    public List<SlotInfo> getSlots(String officeId, LocalDate date) {
        Office office = catalog.getOffice(officeId);
        validateDate(date);
        Map<LocalTime, Long> takenByTime = repository.countActiveByTime(office.getId(), date, SLOT_TIMES);
        List<SlotInfo> slots = new ArrayList<>();
        for (LocalTime time : SLOT_TIMES) {
            int taken = takenByTime.getOrDefault(time, 0L).intValue();
            int remaining = Math.max(0, office.getCapacityPerSlot() - taken);
            boolean inFuture = !LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock));
            slots.add(new SlotInfo(time, office.getCapacityPerSlot(), remaining, inFuture && remaining > 0));
        }
        return slots;
    }

    public synchronized Appointment book(String serviceId, String officeId, LocalDate date, LocalTime time,
                                         Applicant applicant, Business business) {
        PermitService service = catalog.getService(serviceId);
        Office office = catalog.getOffice(officeId);

        if (!office.offers(service.getId())) {
            throw new BookingException(office.getName() + " does not offer " + service.getName().toLowerCase()
                    + ". Please choose another office.");
        }
        if (!office.serves(business.getSize())) {
            throw new BookingException(office.getName() + " does not serve "
                    + business.getSize().getLabel().toLowerCase() + " businesses. Please choose the main office.");
        }
        validateSlot(office, date, time);

        String businessKey = business.getName().toLowerCase();
        boolean alreadyBooked = repository.findActiveByEmail(applicant.getEmail()).stream()
                .anyMatch(a -> a.getService().getId().equals(service.getId())
                        && a.getBusiness().getName().equalsIgnoreCase(businessKey));
        if (alreadyBooked) {
            throw new BookingException("You already have an active booking for " + service.getName().toLowerCase()
                    + " for " + business.getName() + ". Cancel it first to book a new one.");
        }

        Appointment appointment = new Appointment(newReference(date), applicant, business, service, office,
                date, time, service.quote(business), Instant.now(clock));
        repository.save(appointment);
        return appointment;
    }

    public Appointment find(String referenceNo) {
        return repository.findByReference(referenceNo)
                .orElseThrow(() -> new NotFoundException("No booking found for that reference number."));
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
            throw new BookingException("A cancelled booking cannot be moved.");
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
        if (date.isAfter(lastBookableDate())) {
            throw new BookingException("Slots open up to " + MAX_MONTHS_AHEAD + " months in advance, until "
                    + lastBookableDate().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH)) + ".");
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new BookingException("Offices are closed on weekends. Please choose a weekday.");
        }
        holidays.holidayOn(date).ifPresent(h -> {
            throw new BookingException("Offices are closed on " + h.name() + ", a regular holiday. Please choose another day.");
        });
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
            StringBuilder sb = new StringBuilder("BPL-")
                    .append(date.toString().replace("-", "")).append('-');
            for (int i = 0; i < 5; i++) {
                sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
            }
            ref = sb.toString();
        } while (repository.findByReference(ref).isPresent());
        return ref;
    }
}
