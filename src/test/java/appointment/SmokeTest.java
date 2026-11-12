package appointment;

import appointment.model.Appointment;
import appointment.model.Citizen;
import appointment.repository.InMemoryAppointmentRepository;
import appointment.service.AppointmentService;
import appointment.service.BookingException;
import appointment.service.CatalogService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Plain-Java checks of the booking rules (no test framework needed).
 * Run with: ./run-tests.sh
 * The clock is frozen on Thursday 2026-11-12 at 07:00 so results never depend on today's date.
 */
public class SmokeTest {

    private static int passed = 0;

    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T23:00:00Z"), ZoneId.of("Asia/Manila"));

        check("books a valid appointment", () -> {
            AppointmentService s = fresh(clock);
            Appointment a = s.book("lic-renew", "central", d("2026-11-16"), t(9), adult("a@example.com"));
            expect(a.getReferenceNo().matches("APT-20261116-[A-Z2-9]{5}"), "reference format: " + a.getReferenceNo());
            expect(a.isActive(), "should be confirmed");
        });

        check("rejects a second active booking of the same service", () -> {
            AppointmentService s = fresh(clock);
            s.book("lic-renew", "central", d("2026-11-16"), t(9), adult("a@example.com"));
            expectFail(() -> s.book("lic-renew", "central", d("2026-11-17"), t(10), adult("a@example.com")));
        });

        check("allows a different service for the same person", () -> {
            AppointmentService s = fresh(clock);
            s.book("lic-renew", "central", d("2026-11-16"), t(9), adult("a@example.com"));
            s.book("reg-renew", "central", d("2026-11-16"), t(10), adult("a@example.com"));
        });

        check("enforces slot capacity and frees it on cancel", () -> {
            AppointmentService s = fresh(clock); // north office holds 2 per slot
            Appointment first = s.book("lic-renew", "north", d("2026-11-16"), t(9), adult("a@example.com"));
            s.book("lic-renew", "north", d("2026-11-16"), t(9), adult("b@example.com"));
            expectFail(() -> s.book("lic-renew", "north", d("2026-11-16"), t(9), adult("c@example.com")));
            s.cancel(first.getReferenceNo());
            s.book("lic-renew", "north", d("2026-11-16"), t(9), adult("c@example.com"));
        });

        check("rejects weekends, past dates and dates beyond 30 days", () -> {
            AppointmentService s = fresh(clock);
            expectFail(() -> s.book("lic-renew", "central", d("2026-11-14"), t(9), adult("a@example.com"))); // Saturday
            expectFail(() -> s.book("lic-renew", "central", d("2026-11-11"), t(9), adult("a@example.com"))); // yesterday
            expectFail(() -> s.book("lic-renew", "central", d("2026-12-30"), t(9), adult("a@example.com"))); // too far
        });

        check("rejects a time that already passed today and a non-slot time", () -> {
            AppointmentService s = fresh(Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Manila"))); // 11:00 local
            expectFail(() -> s.book("lic-renew", "central", d("2026-11-12"), t(9), adult("a@example.com")));
            expectFail(() -> s.book("lic-renew", "central", d("2026-11-13"), LocalTime.of(12, 0), adult("a@example.com")));
            s.book("lic-renew", "central", d("2026-11-12"), t(14), adult("a@example.com"));
        });

        check("enforces minimum age per service", () -> {
            AppointmentService s = fresh(clock);
            Citizen fifteen = new Citizen("Young Person", "y@example.com", "09171234567", d("2011-06-01"));
            expectFail(() -> s.book("lic-student", "central", d("2026-11-16"), t(9), fifteen));
        });

        check("rejects a service the office does not offer", () -> {
            AppointmentService s = fresh(clock);
            expectFail(() -> s.book("reg-new", "north", d("2026-11-16"), t(9), adult("a@example.com")));
        });

        check("cancel twice fails; cancelled appointments cannot be rescheduled", () -> {
            AppointmentService s = fresh(clock);
            Appointment a = s.book("lic-renew", "central", d("2026-11-16"), t(9), adult("a@example.com"));
            s.cancel(a.getReferenceNo());
            expectFail(() -> s.cancel(a.getReferenceNo()));
            expectFail(() -> s.reschedule(a.getReferenceNo(), d("2026-11-17"), t(10)));
        });

        check("reschedule moves the booking and rejects the same slot", () -> {
            AppointmentService s = fresh(clock);
            Appointment a = s.book("lic-renew", "central", d("2026-11-16"), t(9), adult("a@example.com"));
            expectFail(() -> s.reschedule(a.getReferenceNo(), d("2026-11-16"), t(9)));
            s.reschedule(a.getReferenceNo(), d("2026-11-17"), t(10));
            expect(s.find(a.getReferenceNo()).getDate().equals(d("2026-11-17")), "date should change");
        });

        check("subclasses add their own required documents (polymorphism)", () -> {
            CatalogService c = new CatalogService();
            expect(c.getService("lic-renew").getRequirements().contains("One valid government-issued ID"), "license ID");
            expect(c.getService("reg-renew").getRequirements().contains("Valid insurance certificate"), "insurance");
            expect(!c.getService("lic-renew").getRequirements().contains("Valid insurance certificate"), "no insurance for license");
        });

        check("citizen validation rejects bad input", () -> {
            expectIllegal(() -> new Citizen("A", "a@example.com", "09171234567", d("1990-01-01")));
            expectIllegal(() -> new Citizen("Juan Dela Cruz", "not-an-email", "09171234567", d("1990-01-01")));
            expectIllegal(() -> new Citizen("Juan Dela Cruz", "a@example.com", "abc", d("1990-01-01")));
        });

        System.out.println("\nAll " + passed + " checks passed.");
    }

    // ---- helpers ----

    private static AppointmentService fresh(Clock clock) {
        return new AppointmentService(new InMemoryAppointmentRepository(), new CatalogService(), clock);
    }

    private static Citizen adult(String email) {
        return new Citizen("Juan Dela Cruz", email, "09171234567", d("1990-01-01"));
    }

    private static LocalDate d(String iso) { return LocalDate.parse(iso); }
    private static LocalTime t(int hour) { return LocalTime.of(hour, 0); }

    private interface Block { void run(); }

    private static void check(String name, Block block) {
        try {
            block.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (RuntimeException | Error e) {
            System.out.println("FAIL  " + name + " -> " + e);
            System.exit(1);
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFail(Block block) {
        try {
            block.run();
        } catch (BookingException e) {
            return;
        }
        throw new AssertionError("expected a BookingException");
    }

    private static void expectIllegal(Block block) {
        try {
            block.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError("expected an IllegalArgumentException");
    }
}
