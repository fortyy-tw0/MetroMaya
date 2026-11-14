package appointment.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Philippines' regular holidays (Republic Act No. 9492, as amended), when government offices are closed.
 *
 * <p>Most are worked out from the calendar, so every year is covered: the fixed dates, Maundy Thursday and
 * Good Friday (from Easter), and National Heroes Day (last Monday of August). This matches Proclamation
 * No. 1006 (2026) and Proclamation No. 1427 (2027). Eid'l Fitr and Eid'l Adha are proclaimed separately each
 * year once their dates are known, so they are listed in {@link #PROCLAIMED}: add each new proclamation there.
 */
public class PhilippineHolidayCalendar implements HolidayCalendar {

    /** Holidays proclaimed one by one (Eid'l Fitr, Eid'l Adha). Add new proclamations here. */
    // e.g. Map.of(LocalDate.of(2027, 3, 10), "Eid'l Fitr"). None for 2027 have been proclaimed yet (Nov 2026).
    private static final Map<LocalDate, String> PROCLAIMED = Map.of();

    @Override
    public Optional<Holiday> holidayOn(LocalDate date) {
        return forYear(date.getYear()).stream().filter(h -> h.date().equals(date)).findFirst();
    }

    @Override
    public List<Holiday> between(LocalDate from, LocalDate to) {
        List<Holiday> out = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            for (Holiday h : forYear(year)) {
                if (!h.date().isBefore(from) && !h.date().isAfter(to)) out.add(h);
            }
        }
        out.sort(Comparator.comparing(Holiday::date));
        return out;
    }

    /** All regular holidays in a year. */
    List<Holiday> forYear(int year) {
        LocalDate easter = easterSunday(year);
        List<Holiday> list = new ArrayList<>(List.of(
                new Holiday(LocalDate.of(year, Month.JANUARY, 1), "New Year's Day"),
                new Holiday(easter.minusDays(3), "Maundy Thursday"),
                new Holiday(easter.minusDays(2), "Good Friday"),
                new Holiday(LocalDate.of(year, Month.APRIL, 9), "Araw ng Kagitingan"),
                new Holiday(LocalDate.of(year, Month.MAY, 1), "Labor Day"),
                new Holiday(LocalDate.of(year, Month.JUNE, 12), "Independence Day"),
                new Holiday(LocalDate.of(year, Month.AUGUST, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)),
                        "National Heroes Day"),
                new Holiday(LocalDate.of(year, Month.NOVEMBER, 30), "Bonifacio Day"),
                new Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Christmas Day"),
                new Holiday(LocalDate.of(year, Month.DECEMBER, 30), "Rizal Day")));
        PROCLAIMED.forEach((date, name) -> { if (date.getYear() == year) list.add(new Holiday(date, name)); });
        return list;
    }

    /** Western Easter Sunday (the anonymous Gregorian algorithm). */
    static LocalDate easterSunday(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
