package appointment.service;

import appointment.util.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks that the person booking is a real, verified citizen, using eGov SSO ("Login as eGov").
 *
 * <p>The eGovPH widget on the booking page runs the citizen's OTP and eGov PIN and gives the browser a
 * single-use exchange code. {@link #redeem} trades that code for the citizen's profile on the server
 * (the partner secret never reaches the browser); {@link VerificationPass} then vouches for them.
 *
 * <p>Only the eGov ID, name, email and mobile are kept. The rest of the profile (national ID, photo,
 * signature, addresses) is dropped as soon as it is read and is never logged.
 */
public class EgovVerifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final String partnerCode;
    private final String partnerSecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public EgovVerifier(String baseUrl, String partnerCode, String partnerSecret) {
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.partnerCode = partnerCode.trim();
        this.partnerSecret = partnerSecret.trim();
    }

    /** Safe to send to the browser: the widget needs both. */
    public String getPartnerCode() { return partnerCode; }
    public String getBaseUrl() { return baseUrl; }

    // ---------- eGov SSO ----------

    /** Trades the widget's exchange code for the verified citizen. */
    @SuppressWarnings("unchecked")
    public VerifiedPerson redeem(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank() || exchangeCode.length() > 200) {
            throw new BookingException("eGovPH verification did not finish. Please try again.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exchange_code", exchangeCode.trim());
        body.put("scope", "SSO_AUTHENTICATION");
        body.put("partner_code", partnerCode);
        body.put("partner_secret", partnerSecret);
        Map<String, Object> token = post("/api/token", body, null);
        String accessToken = str(token.get("access_token"));
        if (accessToken == null) throw unavailable("no access token");

        Map<String, Object> profile = post("/api/partner/sso_authentication", Map.of(), accessToken);
        Object data = profile.get("data");
        if (!(data instanceof Map)) throw unavailable("no profile data");
        Map<String, Object> p = (Map<String, Object>) data;

        String id = str(p.get("uniqid"));
        String name = Stream.of("first_name", "middle_name", "last_name", "suffix")
                .map(k -> str(p.get(k))).filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        if (id == null || name.isBlank()) {
            throw new BookingException("Your eGovPH profile did not share your name. Allow name sharing and try again.");
        }
        VerifiedPerson.Address address = VerifiedPerson.Address.of(str(p.get("street")), str(p.get("barangay")),
                str(p.get("municipality")), str(p.get("province")), str(p.get("postal")));
        return new VerifiedPerson("egov:" + id, name, lower(str(p.get("email"))), localMobile(str(p.get("mobile"))),
                "eGovPH", address);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body, String bearer) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);

        HttpResponse<String> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("interrupted");
        } catch (java.io.IOException e) {
            throw unavailable(e.getClass().getSimpleName());
        }
        int status = res.statusCode();
        if (status / 100 == 2) {
            Object parsed = Json.parse(res.body());
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
            throw unavailable("unexpected response");
        }
        // never log the body: it may hold personal data
        System.err.println("eGov SSO " + path + " answered " + status);
        if (status == 422 || status == 401) {
            throw new BookingException("Your eGovPH verification expired. Please verify again.");
        }
        throw unavailable("status " + status);
    }

    private static IllegalStateException unavailable(String why) {
        System.err.println("eGov SSO unavailable: " + why);
        return new IllegalStateException("eGovPH verification is not available right now. Please try again in a moment.");
    }

    /** "+639171234567" or "639171234567" → "09171234567"; anything else → null (the user types it). */
    static String localMobile(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("639")) return "0" + digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("09")) return digits;
        if (digits.length() == 10 && digits.startsWith("9")) return "0" + digits;
        return null;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("N/A") ? null : s;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase();
    }
}
