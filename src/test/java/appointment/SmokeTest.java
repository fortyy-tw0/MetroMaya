package appointment;

import appointment.model.Applicant;
import appointment.model.Appointment;
import appointment.model.Business;
import appointment.model.BusinessSize;
import appointment.model.InspectionType;
import appointment.model.PermitService;
import appointment.model.Quote;
import appointment.repository.InMemoryAppointmentRepository;
import appointment.service.AppointmentService;
import appointment.service.BookingException;
import appointment.service.CatalogService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Plain-Java checks of the pricing and booking rules (no test framework needed).
 * Run with: ./run-tests.sh
 * The clock is frozen on Thursday 2026-11-12 at 07:00 so results never depend on today's date.
 */
public class SmokeTest {

    private static final CatalogService CATALOG = new CatalogService();
    private static int passed = 0;

    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T23:00:00Z"), ZoneId.of("Asia/Manila"));

        // ---------- polymorphism: size ----------

        check("size tier comes from the employee count", () -> {
            expect(BusinessSize.fromEmployees(1) == BusinessSize.MICRO, "1");
            expect(BusinessSize.fromEmployees(9) == BusinessSize.MICRO, "9");
            expect(BusinessSize.fromEmployees(10) == BusinessSize.SMALL, "10");
            expect(BusinessSize.fromEmployees(99) == BusinessSize.SMALL, "99");
            expect(BusinessSize.fromEmployees(100) == BusinessSize.MEDIUM, "100");
            expect(BusinessSize.fromEmployees(199) == BusinessSize.MEDIUM, "199");
            expect(BusinessSize.fromEmployees(200) == BusinessSize.LARGE, "200");
            expectIllegal(() -> BusinessSize.fromEmployees(0));
        });

        check("the same industry costs more as the business grows", () -> {
            double micro = total("new-permit", "food", 5);
            double small = total("new-permit", "food", 50);
            double medium = total("new-permit", "food", 150);
            double large = total("new-permit", "food", 300);
            expect(micro < small && small < medium && medium < large,
                    micro + " < " + small + " < " + medium + " < " + large);
        });

        check("each size tier adds its own inspections and documents", () -> {
            Quote small = quote("new-permit", "services", 20);
            Quote large = quote("new-permit", "services", 300);
            expect(small.inspections().equals(List.of(InspectionType.FIRE_SAFETY)), "small: " + small.inspections());
            expect(large.inspections().containsAll(List.of(InspectionType.FIRE_SAFETY,
                    InspectionType.BUILDING, InspectionType.ENVIRONMENTAL)), "large: " + large.inspections());
            expect(!small.requirements().contains("Latest audited financial statements"), "small has no audit");
            expect(large.requirements().contains("Latest audited financial statements"), "large needs audit");
        });

        // ---------- polymorphism: industry ----------

        check("each industry requires different inspections", () -> {
            expect(quote("new-permit", "food", 5).inspections()
                    .equals(List.of(InspectionType.SANITARY, InspectionType.FIRE_SAFETY)), "food");
            expect(quote("new-permit", "manufacturing", 5).inspections()
                    .contains(InspectionType.ENVIRONMENTAL), "manufacturing");
            expect(!quote("new-permit", "retail", 5).inspections()
                    .contains(InspectionType.ENVIRONMENTAL), "retail");
            expect(quote("new-permit", "healthcare", 5).inspections()
                    .contains(InspectionType.SANITARY), "healthcare");
        });

        check("each industry requires different documents", () -> {
            expect(quote("new-permit", "food", 5).requirements()
                    .contains("Health certificates for all food handlers"), "food");
            expect(quote("new-permit", "healthcare", 5).requirements()
                    .contains("Health department licence to operate"), "healthcare");
            expect(!quote("new-permit", "retail", 5).requirements()
                    .contains("Health certificates for all food handlers"), "retail");
        });

        // ---------- polymorphism: service type ----------

        check("new permit fee is worked out line by line", () -> {
            // Food, micro: permit 2200 + sanitary permit 350 + sanitary 250 + fire 400 + filing 150
            Quote q = quote("new-permit", "food", 5);
            expect(q.fee().getTotal() == 3350.00, "total was " + q.fee().getTotal());
            expect(q.fee().getLines().size() == 5, "lines: " + q.fee().getLines());
        });

        check("renewal is cheaper than a new permit and repeats only recurring inspections", () -> {
            // Retail, small: 1500 x 0.8 x 1.75 = 2100, fire 400 x 1.25 = 500, filing 100. Building is not repeated.
            Quote renewal = quote("renewal", "retail", 50);
            expect(renewal.fee().getTotal() == 2700.00, "total was " + renewal.fee().getTotal());
            expect(renewal.inspections().equals(List.of(InspectionType.FIRE_SAFETY)), "inspections " + renewal.inspections());
            expect(renewal.fee().getTotal() < quote("new-permit", "retail", 50).fee().getTotal(), "cheaper than new");
        });

        check("renewal does not ask for industry documents again", () -> {
            expect(!quote("renewal", "food", 5).requirements()
                    .contains("Health certificates for all food handlers"), "renewal docs");
            expect(quote("renewal", "food", 5).requirements()
                    .contains("Last year's mayor's permit"), "last year's permit");
        });

        check("inspection visit has no permit fee and its time adds up", () -> {
            // Manufacturing, large: building 700 + fire 800 + environmental 1200 + dispatch 100
            Quote q = quote("inspection", "manufacturing", 300);
            expect(q.fee().getTotal() == 2800.00, "total was " + q.fee().getTotal());
            expect(q.fee().getLines().stream().noneMatch(l -> l.label().contains("permit fee")), "no permit fee");
            expect(q.estimatedMinutes() == 390, "minutes were " + q.estimatedMinutes()); // (60+45+90) x 2.0
        });

        // ---------- booking rules ----------

        check("books a valid slot and saves the quote", () -> {
            AppointmentService s = fresh(clock);
            Appointment a = book(s, "renewal", "main", "2026-11-16", 9, "a@example.com", "Aling Nena Bakery", "food", 8);
            expect(a.getReferenceNo().matches("BPL-20261116-[A-Z2-9]{5}"), "reference: " + a.getReferenceNo());
            expect(a.isActive(), "confirmed");
            expect(a.getQuote().fee().getTotal() > 0, "quote saved");
        });

        check("blocks a second active booking for the same business and service", () -> {
            AppointmentService s = fresh(clock);
            book(s, "renewal", "main", "2026-11-16", 9, "a@example.com", "Aling Nena Bakery", "food", 8);
            expectFail(() -> book(s, "renewal", "main", "2026-11-17", 10, "a@example.com", "Aling Nena Bakery", "food", 8));
            book(s, "renewal", "main", "2026-11-17", 10, "a@example.com", "Nena Sari-Sari Store", "retail", 3); // other business
            book(s, "inspection", "main", "2026-11-18", 10, "a@example.com", "Aling Nena Bakery", "food", 8);     // other service
        });

        check("enforces slot capacity and frees it on cancel", () -> {
            AppointmentService s = fresh(clock); // north desk holds 2 per slot
            Appointment first = book(s, "renewal", "north", "2026-11-16", 9, "a@example.com", "Shop A", "retail", 5);
            book(s, "renewal", "north", "2026-11-16", 9, "b@example.com", "Shop B", "retail", 5);
            expectFail(() -> book(s, "renewal", "north", "2026-11-16", 9, "c@example.com", "Shop C", "retail", 5));
            s.cancel(first.getReferenceNo());
            book(s, "renewal", "north", "2026-11-16", 9, "c@example.com", "Shop C", "retail", 5);
        });

        check("rejects weekends, past dates and dates beyond 30 days", () -> {
            AppointmentService s = fresh(clock);
            expectFail(() -> book(s, "renewal", "main", "2026-11-14", 9, "a@example.com", "Shop A", "retail", 5)); // Saturday
            expectFail(() -> book(s, "renewal", "main", "2026-11-11", 9, "a@example.com", "Shop A", "retail", 5)); // yesterday
            expectFail(() -> book(s, "renewal", "main", "2026-12-30", 9, "a@example.com", "Shop A", "retail", 5)); // too far
        });

        check("rejects a time that already passed today and a non-slot time", () -> {
            AppointmentService s = new AppointmentService(new InMemoryAppointmentRepository(), CATALOG,
                    Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Manila"))); // 11:00 local
            expectFail(() -> book(s, "renewal", "main", "2026-11-12", 9, "a@example.com", "Shop A", "retail", 5));
            expectFail(() -> s.book("renewal", "main", d("2026-11-13"), LocalTime.of(12, 0),
                    owner("a@example.com"), business("Shop A", "retail", 5)));
            book(s, "renewal", "main", "2026-11-12", 14, "a@example.com", "Shop A", "retail", 5);
        });

        check("offices only take the services and sizes they are set up for", () -> {
            AppointmentService s = fresh(clock);
            // South desk does not process new permits.
            expectFail(() -> book(s, "new-permit", "south", "2026-11-16", 9, "a@example.com", "Shop A", "retail", 5));
            // North desk does not serve large businesses; the main office does.
            expectFail(() -> book(s, "renewal", "north", "2026-11-16", 9, "b@example.com", "Big Plant", "manufacturing", 300));
            book(s, "renewal", "main", "2026-11-16", 9, "b@example.com", "Big Plant", "manufacturing", 300);
        });

        check("cancelling twice fails and a cancelled booking cannot be moved", () -> {
            AppointmentService s = fresh(clock);
            Appointment a = book(s, "renewal", "main", "2026-11-16", 9, "a@example.com", "Shop A", "retail", 5);
            s.cancel(a.getReferenceNo());
            expectFail(() -> s.cancel(a.getReferenceNo()));
            expectFail(() -> s.reschedule(a.getReferenceNo(), d("2026-11-17"), LocalTime.of(10, 0)));
        });

        check("reschedule moves the booking and rejects the same slot", () -> {
            AppointmentService s = fresh(clock);
            Appointment a = book(s, "renewal", "main", "2026-11-16", 9, "a@example.com", "Shop A", "retail", 5);
            expectFail(() -> s.reschedule(a.getReferenceNo(), d("2026-11-16"), LocalTime.of(9, 0)));
            s.reschedule(a.getReferenceNo(), d("2026-11-17"), LocalTime.of(10, 0));
            expect(s.find(a.getReferenceNo()).getDate().equals(d("2026-11-17")), "date changed");
        });

        check("input validation rejects bad business and owner details", () -> {
            expectIllegal(() -> new Business("A", "12 Rizal St, Brgy 1", CATALOG.getIndustry("retail"), 5));
            expectIllegal(() -> new Business("Shop A", "x", CATALOG.getIndustry("retail"), 5));
            expectIllegal(() -> new Business("Shop A", "12 Rizal St, Brgy 1", CATALOG.getIndustry("retail"), 0));
            expectIllegal(() -> new Applicant("Juan Dela Cruz", "not-an-email", "09171234567"));
            expectIllegal(() -> new Applicant("Juan Dela Cruz", "a@example.com", "abc"));
        });

        System.out.println("\nAll " + passed + " checks passed.");
    }

    // ---- helpers ----

    private static AppointmentService fresh(Clock clock) {
        return new AppointmentService(new InMemoryAppointmentRepository(), new CatalogService(), clock);
    }

    private static Quote quote(String serviceId, String industryId, int employees) {
        PermitService service = CATALOG.getService(serviceId);
        return service.quote(Business.forEstimate(CATALOG.getIndustry(industryId), employees));
    }

    private static double total(String serviceId, String industryId, int employees) {
        return quote(serviceId, industryId, employees).fee().getTotal();
    }

    private static Applicant owner(String email) {
        return new Applicant("Juan Dela Cruz", email, "09171234567");
    }

    private static Business business(String name, String industryId, int employees) {
        return new Business(name, "12 Rizal Street, Barangay 1", CATALOG.getIndustry(industryId), employees);
    }

    private static Appointment book(AppointmentService s, String serviceId, String officeId, String date, int hour,
                                    String email, String businessName, String industryId, int employees) {
        return s.book(serviceId, officeId, d(date), LocalTime.of(hour, 0),
                owner(email), business(businessName, industryId, employees));
    }

    private static LocalDate d(String iso) { return LocalDate.parse(iso); }

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
