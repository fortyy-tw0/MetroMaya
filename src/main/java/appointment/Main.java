package appointment;

import appointment.repository.InMemoryAppointmentRepository;
import appointment.service.AppointmentService;
import appointment.service.CatalogService;
import appointment.web.WebServer;

import java.time.Clock;
import java.time.ZoneId;

/** Entry point. Port comes from the PORT environment variable (default 8080). */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        ZoneId zone = ZoneId.of(System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Manila"));

        AppointmentService service = new AppointmentService(
                new InMemoryAppointmentRepository(),
                new CatalogService(),
                Clock.system(zone));

        new WebServer(port, service).start();
        System.out.println("Appointment system running on http://localhost:" + port);
    }
}
