package appointment;

import appointment.repository.AppointmentRepository;
import appointment.repository.InMemoryAppointmentRepository;
import appointment.repository.SupabaseAppointmentRepository;
import appointment.repository.StaffAccountRepository;
import appointment.repository.InMemoryStaffAccountRepository;
import appointment.repository.SupabaseStaffAccountRepository;
import appointment.repository.OfficePolicyRepository;
import appointment.repository.InMemoryOfficePolicyRepository;
import appointment.repository.SupabaseOfficePolicyRepository;
import appointment.repository.NotificationEventRepository;
import appointment.repository.InMemoryNotificationEventRepository;
import appointment.repository.SupabaseNotificationEventRepository;
import appointment.service.AppointmentService;
import appointment.service.CatalogService;
import appointment.service.EgovVerifier;
import appointment.service.IdScanner;
import appointment.service.PhilippineHolidayCalendar;
import appointment.service.VerificationPass;
import appointment.service.StaffAuthService;
import appointment.service.ManagementLinkVault;
import appointment.service.NotificationService;
import appointment.web.WebServer;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Entry point. Port comes from the PORT environment variable (default 8080).
 *
 * <p>Bookings go to Supabase when SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SECRET_KEY) are
 * set, as on the live site; without them (a quick local run) they are kept in memory.
 *
 * <p>When eGov SSO (EGOV_BASE_URL, EGOV_PARTNER_CODE, EGOV_PARTNER_SECRET) and/or ID scanning (ASSEMBLYAI_API_KEY
 * plus the tesseract binary) are set up, the booking page starts by asking the person to verify with eGovPH or
 * scan a government ID; without them anyone can book, as before.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        ZoneId zone = ZoneId.of(System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Manila"));

        CatalogService catalog = new CatalogService();
        ManagementLinkVault linkVault = new ManagementLinkVault(env("MANAGEMENT_LINK_KEY"));
        AppointmentService service = new AppointmentService(
                repository(catalog),
                catalog,
                Clock.system(zone),
                new PhilippineHolidayCalendar(),
                policyRepository(),
                linkVault);

        Clock clock = Clock.system(zone);
        EgovVerifier egov = egov();
        IdScanner ids = idScanner(clock);
        VerificationPass passes = null;
        if (egov != null || ids != null) {
            // passes are signed with a key derived from the server's eGov secrets, so every instance agrees
            String secret = env("VERIFY_PASS_SECRET");
            if (secret == null) secret = env("EGOV_PARTNER_SECRET") + "|" + assemblyAiKey();
            passes = new VerificationPass(secret, clock);
            System.out.println("Booking requires verification first: "
                    + (egov != null ? "eGovPH SSO" : "") + (egov != null && ids != null ? " or " : "")
                    + (ids != null ? "ID scan" : "") + ".");
        } else {
            System.out.println("No eGov settings: anyone can book without verifying.");
        }
        StaffAuthService staff = new StaffAuthService(staffRepository(), catalog, clock,
                env("STAFF_ADMIN_USERNAME"), env("STAFF_ADMIN_PASSWORD"), env("STAFF_SESSION_SECRET"));
        NotificationService notifications = new NotificationService(service, notificationRepository(), linkVault, clock,
                env("RESEND_API_KEY"), env("RESEND_FROM_EMAIL"), env("PUBLIC_BASE_URL"), env("RESEND_API_URL"));
        if (!staff.configured()) System.out.println("Staff login is off until STAFF_ADMIN_PASSWORD and a 32-character STAFF_SESSION_SECRET are set.");
        if (!notifications.configured()) System.out.println("Booking email is off until Resend, PUBLIC_BASE_URL and MANAGEMENT_LINK_KEY are configured.");
        new WebServer(port, service, egov, ids, passes, staff, notifications).start();
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

    private static StaffAccountRepository staffRepository() {
        String url = env("SUPABASE_URL");
        String key = env("SUPABASE_SERVICE_ROLE_KEY");
        if (key == null) key = env("SUPABASE_SECRET_KEY");
        return url != null && key != null ? new SupabaseStaffAccountRepository(url, key)
                : new InMemoryStaffAccountRepository();
    }

    private static OfficePolicyRepository policyRepository() {
        String url = env("SUPABASE_URL");
        String key = env("SUPABASE_SERVICE_ROLE_KEY");
        if (key == null) key = env("SUPABASE_SECRET_KEY");
        return url != null && key != null ? new SupabaseOfficePolicyRepository(url, key)
                : new InMemoryOfficePolicyRepository();
    }

    private static NotificationEventRepository notificationRepository() {
        String url = env("SUPABASE_URL");
        String key = env("SUPABASE_SERVICE_ROLE_KEY");
        if (key == null) key = env("SUPABASE_SECRET_KEY");
        return url != null && key != null ? new SupabaseNotificationEventRepository(url, key)
                : new InMemoryNotificationEventRepository();
    }

    /** "Verify with eGovPH" (eGov SSO), when EGOV_BASE_URL, EGOV_PARTNER_CODE and EGOV_PARTNER_SECRET are set. */
    private static EgovVerifier egov() {
        String url = env("EGOV_BASE_URL"), code = env("EGOV_PARTNER_CODE"), secret = env("EGOV_PARTNER_SECRET");
        return url != null && code != null && secret != null ? new EgovVerifier(url, code, secret) : null;
    }

    /**
     * "Scan your ID": Tesseract OCR on this server, then an LLM through AssemblyAI's LLM Gateway sorts the text
     * into fields. On when ASSEMBLYAI_API_KEY is set and the tesseract binary runs (TESSERACT_PATH overrides
     * where it is). ID_SCAN_MODEL picks the gateway model (default claude-sonnet-5-5).
     */
    private static IdScanner idScanner(Clock clock) {
        String key = assemblyAiKey();
        if (key == null) return null;
        String tesseract = System.getenv().getOrDefault("TESSERACT_PATH", "tesseract");
        if (!IdScanner.ocrAvailable(tesseract)) {
            System.out.println("ASSEMBLYAI_API_KEY is set but tesseract isn't installed: ID scanning is off.");
            return null;
        }
        String model = env("ID_SCAN_MODEL");
        return new IdScanner(key, model == null ? "claude-sonnet-5-5" : model, tesseract, clock);
    }

    /** The AssemblyAI key, tolerating quotes pasted around it. */
    private static String assemblyAiKey() {
        String k = env("ASSEMBLYAI_API_KEY");
        if (k == null) return null;
        k = k.replaceAll("^[\"']+|[\"']+$", "").trim();
        return k.isEmpty() ? null : k;
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
