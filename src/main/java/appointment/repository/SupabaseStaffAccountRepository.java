package appointment.repository;

import appointment.model.StaffAccount;
import appointment.util.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Server-side service-role access only. The public Supabase key must never be used here. */
public class SupabaseStaffAccountRepository implements StaffAccountRepository {
    private final String url;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseStaffAccountRepository(String projectUrl, String key) {
        this.url = projectUrl.trim().replaceAll("/+$", "").replaceAll("/rest/v1$", "")
                + "/rest/v1/staff_accounts";
        this.key = key;
    }

    @Override public Optional<StaffAccount> find(String username) {
        if (username == null || !username.matches("[a-z0-9][a-z0-9._-]{2,39}")) return Optional.empty();
        return read("?username=eq." + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&limit=1")
                .stream().findFirst().map(this::account);
    }

    @Override public List<StaffAccount> all() {
        return read("?select=username,password_hash,role,office_id,active&order=username.asc")
                .stream().map(this::account).toList();
    }

    @Override public void save(StaffAccount a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", a.username());
        row.put("password_hash", a.passwordHash());
        row.put("role", a.role().name());
        row.put("office_id", a.officeId());
        row.put("active", a.active());
        HttpRequest req = request("?on_conflict=username")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(row), StandardCharsets.UTF_8)).build();
        check(send(req));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> read(String query) {
        HttpResponse<String> res = send(request(query).GET().build());
        check(res);
        return (List<Map<String, Object>>) Json.parse(res.body());
    }

    private StaffAccount account(Map<String, Object> row) {
        return new StaffAccount((String) row.get("username"), (String) row.get("password_hash"),
                StaffAccount.Role.valueOf((String) row.get("role")), (String) row.get("office_id"),
                Boolean.TRUE.equals(row.get("active")));
    }

    private HttpRequest.Builder request(String query) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + query)).timeout(Duration.ofSeconds(10))
                .header("apikey", key);
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer " + key);
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try { return client.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (Exception e) { throw new IllegalStateException("Staff account storage is unavailable.", e); }
    }

    private void check(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2)
            throw new IllegalStateException("Staff account storage returned " + response.statusCode() + ".");
    }
}
