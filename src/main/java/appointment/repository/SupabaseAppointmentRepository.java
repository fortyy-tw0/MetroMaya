package appointment.repository;

import appointment.model.Applicant;
import appointment.model.Appointment;
import appointment.model.AppointmentStatus;
import appointment.model.Business;
import appointment.model.BusinessSize;
import appointment.model.FeeBreakdown;
import appointment.model.FeeLine;
import appointment.model.InspectionType;
import appointment.model.Quote;
import appointment.service.BookingException;
import appointment.service.CatalogService;
import appointment.util.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores appointments in a Supabase (PostgreSQL) table through Supabase's REST API, so bookings survive
 * restarts and redeploys. Uses only the JDK's HTTP client, so the project still has no dependencies.
 *
 * <p>The table is created by {@code supabase/migrations/20261114000000_create_appointments.sql}. The server
 * authenticates with the project's secret (service role) key, which never reaches the browser; row level
 * security is on with no public policies, so nothing else can read or change the bookings.
 */
public class SupabaseAppointmentRepository implements AppointmentRepository {

    private static final String TABLE = "appointments";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String restUrl;
    private final String apiKey;
    private final CatalogService catalog;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public SupabaseAppointmentRepository(String projectUrl, String apiKey, CatalogService catalog) {
        // accept the project URL with or without the REST path Supabase's dashboard shows ("…/rest/v1/")
        String base = projectUrl.trim().replaceAll("/+$", "").replaceAll("/rest/v1$", "");
        this.restUrl = base + "/rest/v1/" + TABLE;
        this.apiKey = apiKey;
        this.catalog = catalog;
    }

    // ---------- AppointmentRepository ----------

    @Override
    public void save(Appointment a) {
        String body = Json.stringify(toRow(a));
        HttpResponse<String> res = send(request("?on_conflict=reference_no")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
        if (res.statusCode() == 409) {
            // the database's own guard: one active booking per email + business + service
            throw new BookingException("You already have an active booking for this business and service. "
                    + "Cancel it first to book a new one.");
        }
        check(res, "save the booking");
    }

    @Override
    public Optional<Appointment> findByReference(String referenceNo) {
        if (referenceNo == null || referenceNo.isBlank()) return Optional.empty();
        List<Map<String, Object>> rows = select("reference_no=eq." + enc(referenceNo.trim().toUpperCase()) + "&limit=1");
        return rows.stream().findFirst().map(this::toAppointment);
    }

    @Override
    public long countActive(String officeId, LocalDate date, LocalTime time) {
        return select("select=reference_no&status=eq.CONFIRMED&office_id=eq." + enc(officeId)
                + "&appt_date=eq." + date + "&appt_time=eq." + time).size();
    }

    @Override
    public Map<LocalTime, Long> countActiveByTime(String officeId, LocalDate date, List<LocalTime> times) {
        Map<LocalTime, Long> counts = new HashMap<>();
        for (Map<String, Object> row : select("select=appt_time&status=eq.CONFIRMED&office_id=eq." + enc(officeId)
                + "&appt_date=eq." + date)) {
            counts.merge(LocalTime.parse(str(row, "appt_time")), 1L, Long::sum);
        }
        return counts;
    }

    @Override
    public List<Appointment> findActiveByEmail(String email) {
        if (email == null) return List.of();
        return select("status=eq.CONFIRMED&email=eq." + enc(email.trim().toLowerCase())).stream()
                .map(this::toAppointment)
                .toList();
    }

    // ---------- mapping between rows and objects ----------

    private Map<String, Object> toRow(Appointment a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reference_no", a.getReferenceNo());
        row.put("status", a.getStatus().name());
        row.put("service_id", a.getService().getId());
        row.put("office_id", a.getOffice().getId());
        row.put("appt_date", a.getDate().toString());
        row.put("appt_time", a.getTime().toString());
        row.put("business_name", a.getBusiness().getName());
        row.put("business_address", a.getBusiness().getAddress());
        row.put("industry_id", a.getBusiness().getIndustry().getId());
        row.put("employees", a.getBusiness().getEmployeeCount());
        row.put("owner_name", a.getApplicant().getFullName());
        row.put("email", a.getApplicant().getEmail());
        row.put("phone", a.getApplicant().getPhone());
        row.put("quote", quoteToJson(a.getQuote()));
        row.put("created_at", a.getCreatedAt().toString());
        row.put("updated_at", Instant.now().toString());
        return row;
    }

    private Appointment toAppointment(Map<String, Object> row) {
        Business business = new Business(str(row, "business_name"), str(row, "business_address"),
                catalog.getIndustry(str(row, "industry_id")), ((Number) row.get("employees")).intValue());
        Applicant applicant = new Applicant(str(row, "owner_name"), str(row, "email"), str(row, "phone"));
        return Appointment.restore(str(row, "reference_no"), applicant, business,
                catalog.getService(str(row, "service_id")), catalog.getOffice(str(row, "office_id")),
                LocalDate.parse(str(row, "appt_date")), LocalTime.parse(str(row, "appt_time")),
                quoteFromJson(row.get("quote")), OffsetDateTime.parse(str(row, "created_at")).toInstant(),
                AppointmentStatus.valueOf(str(row, "status")));
    }

    /** The quote is stored as a snapshot, so a booking keeps the fees it was made with. */
    private static Map<String, Object> quoteToJson(Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", q.size().name());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (FeeLine line : q.fee().getLines()) {
            lines.add(Map.of("label", line.label(), "amount", line.amount()));
        }
        m.put("fee", lines);
        m.put("inspections", q.inspections().stream().map(Enum::name).toList());
        m.put("requirements", q.requirements());
        m.put("estimatedMinutes", q.estimatedMinutes());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Quote quoteFromJson(Object value) {
        Map<String, Object> m = (Map<String, Object>) (value instanceof String s ? Json.parse(s) : value);
        FeeBreakdown fee = new FeeBreakdown();
        for (Object o : (List<Object>) m.get("fee")) {
            Map<String, Object> line = (Map<String, Object>) o;
            fee.add((String) line.get("label"), ((Number) line.get("amount")).doubleValue());
        }
        List<InspectionType> inspections = ((List<Object>) m.get("inspections")).stream()
                .map(o -> InspectionType.valueOf((String) o)).toList();
        List<String> requirements = ((List<Object>) m.get("requirements")).stream().map(String::valueOf).toList();
        return new Quote(BusinessSize.valueOf((String) m.get("size")), fee, inspections, requirements,
                ((Number) m.get("estimatedMinutes")).intValue());
    }

    // ---------- HTTP ----------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> select(String query) {
        HttpResponse<String> res = send(request("?" + query).GET());
        check(res, "read the bookings");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object o : (List<Object>) Json.parse(res.body())) rows.add((Map<String, Object>) o);
        return rows;
    }

    private HttpRequest.Builder request(String query) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(restUrl + query))
                .timeout(TIMEOUT)
                .header("apikey", apiKey)
                .header("Accept", "application/json");
        // legacy service_role keys are JWTs and also go in Authorization; new sb_secret_ keys go in apikey only
        if (apiKey.startsWith("eyJ")) b.header("Authorization", "Bearer " + apiKey);
        return b;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The bookings database did not answer. Please try again.", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The bookings database did not answer. Please try again.", e);
        }
    }

    private static void check(HttpResponse<String> res, String what) {
        if (res.statusCode() / 100 != 2) {
            System.err.println("Supabase " + res.statusCode() + " while trying to " + what + ": " + res.body());
            throw new IllegalStateException("Could not " + what + " right now. Please try again in a moment.");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
