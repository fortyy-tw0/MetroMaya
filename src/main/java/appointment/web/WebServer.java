package appointment.web;

import appointment.service.AppointmentService;
import appointment.service.EgovVerifier;
import appointment.service.IdScanner;
import appointment.service.VerificationPass;
import appointment.service.StaffAuthService;
import appointment.service.NotificationService;
import appointment.service.WaitlistService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Wraps the JDK's built-in HTTP server: /api/* goes to the API, everything else to static files. */
public class WebServer {

    private final HttpServer server;
    private final NotificationService notifications;
    private ScheduledExecutorService reminders;

    public WebServer(int port, AppointmentService appointments, EgovVerifier egov, IdScanner idScanner,
                     VerificationPass passes, StaffAuthService staff,
                     NotificationService notifications, WaitlistService waitlist) throws IOException {
        this.notifications = notifications;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/", new ApiHandler(appointments, egov, idScanner, passes, staff, notifications, waitlist));
        server.setExecutor(Executors.newFixedThreadPool(16));
    }

    public void start() {
        server.start();
        if (notifications.configured()) {
            reminders = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "booking-reminders");
                thread.setDaemon(true);
                return thread;
            });
            reminders.scheduleAtFixedRate(() -> {
                try { notifications.remindTomorrow(); }
                catch (Exception e) { System.err.println("Booking reminder check failed: " + e.getClass().getSimpleName()); }
            }, 1, 60, TimeUnit.MINUTES);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (reminders != null) reminders.shutdownNow();
            server.stop(1);
        }));
    }
}
