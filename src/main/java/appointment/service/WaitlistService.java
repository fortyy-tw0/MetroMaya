package appointment.service;

import appointment.model.Office;
import appointment.repository.WaitlistRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Notify me if a place opens": someone can join the list for a fully booked day, and when a booking on that
 * day is cancelled or moved away, everyone waiting gets one email (whoever books first gets the place).
 * Only on while booking emails can be sent.
 */
public class WaitlistService {
    public static final int MAX_DAYS_PER_EMAIL = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppointmentService appointments;
    private final WaitlistRepository repository;
    private final NotificationService notifications;

    public WaitlistService(AppointmentService appointments, WaitlistRepository repository, NotificationService notifications) {
        this.appointments = appointments;
        this.repository = repository;
        this.notifications = notifications;
    }

    public boolean enabled() { return notifications.canSendConfirmation(); }

    public void join(String officeId, LocalDate date, String rawEmail) {
        if (!enabled()) throw new BookingException("The waitlist is turned off right now.");
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) throw new BookingException("Enter a valid email address.");
        Office office = appointments.getCatalog().getOffice(officeId);
        AppointmentService.DayAvailability day = day(office.getId(), date);
        if (!day.open()) throw new BookingException("That day is not open for booking.");
        if (day.remaining() > 0) throw new BookingException("That day has open places again. Pick a time now.");
        if (repository.countWaiting(email, appointments.today()) >= MAX_DAYS_PER_EMAIL)
            throw new BookingException("You're already waiting for " + MAX_DAYS_PER_EMAIL + " days. Book an open day instead.");
        repository.add(office.getId(), date, email);
    }

    /** Called after a booking leaves a day (cancelled or moved). Emails everyone waiting, once. */
    public int placeOpened(String officeId, LocalDate date) {
        if (!enabled() || date == null || date.isBefore(appointments.today())) return 0;
        try {
            AppointmentService.DayAvailability day = day(officeId, date);
            if (!day.open() || day.remaining() == 0) return 0;
            Office office = appointments.getCatalog().getOffice(officeId);
            int sent = 0;
            for (WaitlistRepository.Entry e : repository.waiting(officeId, date)) {
                if (notifications.placeOpened(office, date, e.email(), e.id())) {
                    repository.markNotified(e.id());
                    sent++;
                }
            }
            return sent;
        } catch (RuntimeException e) {
            System.err.println("Waitlist check failed: " + e.getClass().getSimpleName());
            return 0;
        }
    }

    private AppointmentService.DayAvailability day(String officeId, LocalDate date) {
        return appointments.monthAvailability(officeId, YearMonth.from(date)).stream()
                .filter(d -> d.date().equals(date)).findFirst()
                .orElseThrow(() -> new BookingException("That day is not open for booking."));
    }
}
