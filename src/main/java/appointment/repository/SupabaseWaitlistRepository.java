package appointment.repository;

import appointment.util.Json;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The waitlist table, through the server's private Supabase key (migration 20261123000000_add_waitlist.sql). */
public class SupabaseWaitlistRepository implements WaitlistRepository {
    private final String url;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseWaitlistRepository(String projectUrl, String key) {
        url = projectUrl.trim().replaceAll("/+$", "").replaceAll("/rest/v1$", "") + "/rest/v1/waitlist";
        this.key = key;
    }

    @Override
    public void add(String officeId, LocalDate date, String email) {
        HttpRequest request = request("")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(
                        Map.of("office_id", officeId, "date", date.toString(), "email", email)))).build();
        HttpResponse<String> response = call(request);
        if (response.statusCode() == 409) return;   // already on the list for that day
        check(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Entry> waiting(String officeId, LocalDate date) {
        String query = "?select=id,office_id,date,email&office_id=eq." + enc(officeId) + "&date=eq." + date
                + "&notified_at=is.null&order=created_at.asc&limit=200";
        List<Entry> out = new ArrayList<>();
        for (Object o : (List<Object>) Json.parse(check(call(request(query).GET().build())).body())) {
            Map<String, Object> row = (Map<String, Object>) o;
            out.add(new Entry((String) row.get("id"), (String) row.get("office_id"),
                    LocalDate.parse((String) row.get("date")), (String) row.get("email")));
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int countWaiting(String email, LocalDate from) {
        String query = "?select=id&email=eq." + enc(email)
                + "&date=gte." + from + "&notified_at=is.null&limit=50";
        return ((List<Object>) Json.parse(check(call(request(query).GET().build())).body())).size();
    }

    @Override
    public void markNotified(String id) {
        HttpRequest request = request("?id=eq." + enc(id))
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(Json.stringify(
                        Map.of("notified_at", java.time.Instant.now().toString())))).build();
        check(call(request));
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private HttpRequest.Builder request(String query) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + query)).timeout(Duration.ofSeconds(10))
                .header("apikey", key);
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer " + key);
        return builder;
    }

    private HttpResponse<String> call(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The waitlist is not available right now. Please try again later.", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The waitlist is not available right now. Please try again later.", e);
        }
    }

    private static HttpResponse<String> check(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2)
            throw new IllegalStateException("The waitlist is not available right now. Please try again later.");
        return response;
    }
}
