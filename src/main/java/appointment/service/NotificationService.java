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

    /** Everything email needs, including the key that lets reminders rebuild the private link later. */
    public boolean configured() {
        return canSendConfirmation() && vault.configured();
    }

    /**
     * The confirmation email only needs Resend and the site address: it goes out right after booking, while the
     * private access code is still in hand, so it doesn't depend on MANAGEMENT_LINK_KEY.
     */
    public boolean canSendConfirmation() {
        return apiKey != null && from != null && publicBaseUrl != null
                && (publicBaseUrl.startsWith("https://") || publicBaseUrl.startsWith("http://localhost:")
                    || publicBaseUrl.startsWith("http://127.0.0.1:"));
    }

    /**
     * Sent right after booking: the reference number and the private access code (plus a link that opens the
     * booking directly), so the applicant doesn't have to copy them down.
     */
    public boolean confirmation(AppointmentService.BookingResult booking) {
        if (!canSendConfirmation()) return false;
        Appointment a = booking.appointment();
        String code = booking.managementToken();
        String link = link(a, code);
        String office = a.getOffice().getName() + ", " + a.getOffice().getAddress();
        String text = "Your MetroMaya booking is confirmed.\n\n"
                + "Reference number: " + a.getReferenceNo() + "\n"
                + "Private access code: " + code + "\n\n"
                + "Service: " + a.getService().getName() + "\n"
                + "Business: " + a.getBusiness().getName() + "\n"
                + "When: " + when(a) + "\n"
                + "Where: " + office + "\n\n"
                + "To view, move or cancel your booking, open your private link:\n" + link + "\n"
                + "or go to " + publicBaseUrl + "/manage.html and enter your reference number and access code.\n\n"
                + "Show your reference number (or the check-in QR on your booking page) at the office.\n"
                + "Keep the access code private: anyone who has it can change your booking.\n\n"
                + "MetroMaya is a student prototype, not a government website. Fees are sample rates.";
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#3E2416;max-width:560px;margin:0 auto;line-height:1.5\">"
                + "<p style=\"font-size:22px;font-weight:bold;margin:0 0 4px\">MetroMaya<span style=\"color:#EDB24A\">.</span></p>"
                + "<p style=\"margin:0 0 18px\">Your booking is confirmed. Keep this email: it has everything you need to manage it.</p>"
                + "<table style=\"width:100%;border-collapse:collapse;background:#E8EBF6;border-radius:12px\"><tr><td style=\"padding:16px 18px\">"
                + "<p style=\"margin:0;font-size:13px;color:#6B5446\">Reference number</p>"
                + "<p style=\"margin:2px 0 12px;font:bold 22px 'Courier New',monospace;color:#2C3A72;letter-spacing:1px\">" + esc(a.getReferenceNo()) + "</p>"
                + "<p style=\"margin:0;font-size:13px;color:#6B5446\">Private access code</p>"
                + "<p style=\"margin:2px 0 0;font:15px 'Courier New',monospace;color:#2C3A72;word-break:break-all\">" + esc(code) + "</p>"
                + "</td></tr></table>"
                + "<table style=\"width:100%;margin:16px 0;font-size:14px\">"
                + row("Service", a.getService().getName()) + row("Business", a.getBusiness().getName())
                + row("When", when(a)) + row("Where", office) + "</table>"
                + "<p style=\"margin:0 0 16px\"><a href=\"" + esc(link) + "\" style=\"display:inline-block;background:#2C3A72;color:#fff;"
                + "text-decoration:none;padding:11px 20px;border-radius:10px;font-weight:bold\">Open my booking</a></p>"
                + "<p style=\"font-size:13px;color:#6B5446;margin:0 0 6px\">Or go to " + esc(publicBaseUrl) + "/manage.html and enter the reference number and access code.</p>"
                + "<p style=\"font-size:13px;color:#6B5446;margin:0 0 6px\">Keep the access code private: anyone who has it can change your booking.</p>"
                + "<p style=\"font-size:12px;color:#8C7A6E;margin:18px 0 0\">MetroMaya is a student prototype, not a government website. Fees are sample rates and no real payments are made.</p>"
                + "</div>";
        return deliver("confirmation:" + a.getReferenceNo(), a,
                "Your MetroMaya booking " + a.getReferenceNo(), text, html, true);
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
        return deliver(eventKey, a, subject, message, null, false);
    }

    private boolean deliver(String eventKey, Appointment a, String subject, String message, String html,
                            boolean confirmationOnly) {
        if (!(confirmationOnly ? canSendConfirmation() : configured()) || a.isDemoRecord()) return false;
        return send(eventKey, a.getApplicant().getEmail(), subject, message, html);
    }

    /**
     * Waitlist: a place opened on a day that was fully booked. Goes to everyone waiting for that day; whoever
     * books first gets it. Needs only what the confirmation email needs.
     */
    public boolean placeOpened(Office office, LocalDate date, String email, String entryId) {
        if (!canSendConfirmation()) return false;
        String day = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.ENGLISH));
        String link = publicBaseUrl + "/book.html";
        String text = "Good news: a place just opened on " + day + " at " + office.getName() + ".\n\n"
                + "You asked us to tell you. Places go to whoever books first, so book soon:\n" + link + "\n\n"
                + "We only send this once for that day.\n\n"
                + "MetroMaya is a student prototype, not a government website. Fees are sample rates.";
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#3E2416;max-width:560px;margin:0 auto;line-height:1.5\">"
                + "<p style=\"font-size:22px;font-weight:bold;margin:0 0 4px\">MetroMaya<span style=\"color:#EDB24A\">.</span></p>"
                + "<p style=\"margin:0 0 14px\">Good news: a place just opened on <strong>" + esc(day) + "</strong> at "
                + esc(office.getName()) + ".</p>"
                + "<p style=\"margin:0 0 16px\">Places go to whoever books first, so book soon.</p>"
                + "<p style=\"margin:0 0 16px\"><a href=\"" + esc(link) + "\" style=\"display:inline-block;background:#2C3A72;color:#fff;"
                + "text-decoration:none;padding:11px 20px;border-radius:10px;font-weight:bold\">Book a slot</a></p>"
                + "<p style=\"font-size:13px;color:#6B5446;margin:0 0 6px\">You joined the waitlist for this day. We only send this once.</p>"
                + "<p style=\"font-size:12px;color:#8C7A6E;margin:18px 0 0\">MetroMaya is a student prototype, not a government website. Fees are sample rates and no real payments are made.</p>"
                + "</div>";
        return send("waitlist:" + entryId, email, "A place opened on " + day, text, html);
    }

    private boolean send(String eventKey, String to, String subject, String message, String html) {
        try {
            if (events.hasSent(eventKey)) return false;
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("from", from);
            body.put("to", java.util.List.of(to));
            body.put("subject", subject);
            body.put("text", message);
            if (html != null) body.put("html", html);
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

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#8C7A6E;vertical-align:top;white-space:nowrap\">" + esc(label)
                + "</td><td style=\"padding:4px 0;font-weight:bold\">" + esc(value) + "</td></tr>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
