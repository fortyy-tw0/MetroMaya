package appointment;

import appointment.repository.AppointmentRepository;
import appointment.repository.InMemoryAppointmentRepository;
import appointment.repository.SupabaseAppointmentRepository;
import appointment.service.AppointmentService;
import appointment.service.CatalogService;
import appointment.service.PhilippineHolidayCalendar;
import appointment.web.WebServer;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Entry point. Port comes from the PORT environment variable (default 8080).
 *
 * <p>Bookings go to Supabase when SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SECRET_KEY) are
 * set, as on the live site; without them (a quick local run) they are kept in memory.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        ZoneId zone = ZoneId.of(System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Manila"));

        CatalogService catalog = new CatalogService();
        AppointmentService service = new AppointmentService(
                repository(catalog),
                catalog,
                Clock.system(zone),
                new PhilippineHolidayCalendar());

        new WebServer(port, service).start();
        System.out.println("Appointment system running on http://localhost:" + port);
    }

    private static AppointmentRepository repository(CatalogService catalog) {
        String url = env("SUPABASE_URL");
        String key = env("SUPABASE_SERVICE_ROLE_KEY");
        if (key == null) key = env("SUPABASE_SECRET_KEY");
        if (url != null && key != null) {
            System.out.println("Bookings are stored in Supabase.");
            return new SupabaseAppointmentRepository(url, key, catalog);
        }
        System.out.println("SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY not set: bookings are kept in memory only.");
        return new InMemoryAppointmentRepository();
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
