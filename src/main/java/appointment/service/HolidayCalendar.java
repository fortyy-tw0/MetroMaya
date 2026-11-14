package appointment.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Which days government offices are closed for a holiday. Swap the calendar without touching the booking rules. */
public interface HolidayCalendar {

    /** A named holiday on a date. */
    record Holiday(LocalDate date, String name) {}

    /** The holiday on this date, if any. */
    Optional<Holiday> holidayOn(LocalDate date);

    /** Every holiday from {@code from} to {@code to}, both included, in date order. */
    List<Holiday> between(LocalDate from, LocalDate to);
}
