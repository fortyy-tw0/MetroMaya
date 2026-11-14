package appointment.service;

import appointment.model.Appointment;
import appointment.model.Office;
import appointment.repository.NotificationEventRepository;
import appointment.util.Json;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/** Transactional email via Resend. Disabled unless all required server secrets are configured. */
public class NotificationService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH);
    private final AppointmentService appointments;
    private final NotificationEventRepository events;
    private final ManagementLinkVault vault;
    private final Clock clock;
    private final String apiKey;
    private final String from;
    private final String publicBaseUrl;
    private final String endpoint;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public NotificationService(AppointmentService appointments, NotificationEventRepository events,
                               ManagementLinkVault vault, Clock clock, String apiKey, String from,
                               String publicBaseUrl, String endpoint) {
        this.appointments = appointments;
        this.events = events;
        this.vault = vault;
        this.clock = clock;
        this.apiKey = apiKey;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl == null ? null : publicBaseUrl.replaceAll("/+$", "");
        this.endpoint = endpoint == null ? "https://api.resend.com/emails" : endpoint;
    }

    public boolean configured() {
        return apiKey != null && from != null && publicBaseUrl != null && vault.configured()
                && (publicBaseUrl.startsWith("https://") || publicBaseUrl.startsWith("http://localhost:")
                    || publicBaseUrl.startsWith("http://127.0.0.1:"));
    }

    public void confirmation(AppointmentService.BookingResult booking) {
        if (!configured()) return;
        Appointment a = booking.appointment();
        String link = link(a, booking.managementToken());
        deliver("confirmation:" + a.getReferenceNo(), a, "MetroMaya booking confirmation",
                "Your booking " + a.getReferenceNo() + " is confirmed for " + when(a) + " at "
                        + a.getOffice().getName() + ".\n\nManage your booking: " + link
                        + "\n\nKeep this link private. MetroMaya is a prototype, not an official government service.");
    }

    public boolean moved(Appointment a) {
        String token = vault.decrypt(a.getManagementTokenCiphertext());
        if (token == null) return false;
        return deliver("moved:" + a.getReferenceNo() + ":" + a.getDate() + ":" + a.getTime(), a,
                "MetroMaya appointment time changed",
                "Your office changed booking " + a.getReferenceNo() + " to " + when(a) + ".\n\n"
                        + "Review or move it using your private link: " + link(a, token));
    }

    public boolean closure(Appointment a, String reason) {
        String token = vault.decrypt(a.getManagementTokenCiphertext());
        if (token == null) return false;
        return deliver("closure:" + a.getReferenceNo() + ":" + a.getDate() + ":" + digest(reason), a,
                "MetroMaya office closure affects your booking",
                "Your office is closed on " + a.getDate().format(DATE) + " because " + reason + ". "
                        + "Booking " + a.getReferenceNo() + " needs a new time.\n\n"
                        + "Move it using your private link: " + link(a, token));
    }

    /** Run at least daily. The event log and provider idempotency key suppress duplicate reminders. */
    public int remindTomorrow() {
        if (!configured()) return 0;
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        int sent = 0;
        for (Office office : appointments.getCatalog().getOffices()) {
            boolean closed = appointments.policy(office.getId()).closures().containsKey(tomorrow);
            for (Appointment a : appointments.listForOffice(office.getId(), tomorrow, tomorrow)) {
                if (!a.isActive() || a.isWalkIn() || a.isDemoRecord() || a.getAcceptedAt() != null) continue;
                if (closed) continue;
                String token = vault.decrypt(a.getManagementTokenCiphertext());
                if (token == null) continue;
                if (deliver("reminder:" + a.getReferenceNo() + ":" + tomorrow, a,
                        "MetroMaya appointment reminder",
                        "Reminder: booking " + a.getReferenceNo() + " is tomorrow, " + when(a) + " at "
                                + office.getName() + ".\n\nManage your booking: " + link(a, token))) sent++;
            }
        }
        return sent;
    }

    private boolean deliver(String eventKey, Appointment a, String subject, String message) {
        if (!configured() || a.isDemoRecord()) return false;
        try {
            if (events.hasSent(eventKey)) return false;
            Map<String, Object> body = Map.of("from", from, "to", java.util.List.of(a.getApplicant().getEmail()),
                    "subject", subject, "text", message);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", digest(eventKey))
                    .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                System.err.println("Booking email could not be sent (provider status " + response.statusCode() + ").");
                return false;
            }
            events.markSent(eventKey);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.err.println("Booking email could not be sent: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private String link(Appointment a, String token) {
        return publicBaseUrl + "/manage.html?ref=" + URLEncoder.encode(a.getReferenceNo(), StandardCharsets.UTF_8)
                + "#access=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String when(Appointment a) {
        return a.getDate().format(DATE) + " at " + a.getTime().format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    private static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 20);
        } catch (Exception e) { throw new IllegalStateException("Cannot hash notification event.", e); }
    }
}
