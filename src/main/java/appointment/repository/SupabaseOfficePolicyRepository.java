package appointment.repository;

import appointment.model.OfficePolicy;
import appointment.util.Json;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads office policy rows through the server's private Supabase service key. */
public class SupabaseOfficePolicyRepository implements OfficePolicyRepository {
    private final String url;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseOfficePolicyRepository(String projectUrl, String key) {
        url = projectUrl.trim().replaceAll("/+$", "").replaceAll("/rest/v1$", "") + "/rest/v1/office_policies";
        this.key = key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<OfficePolicy> find(String officeId) {
        String query = "?office_id=eq." + URLEncoder.encode(officeId, StandardCharsets.UTF_8) + "&limit=1";
        List<Object> rows = (List<Object>) Json.parse(send(request(query).GET().build()).body());
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = (Map<String, Object>) rows.get(0);
        List<LocalTime> times = ((List<Object>) row.get("slot_times")).stream()
                .map(o -> LocalTime.parse((String) o)).toList();
        Map<LocalDate, OfficePolicy.Closure> closures = new LinkedHashMap<>();
        Map<String, Object> closed = (Map<String, Object>) row.get("closures");
        closed.forEach((date, value) -> {
            Map<String, Object> item = (Map<String, Object>) value;
            closures.put(LocalDate.parse(date), new OfficePolicy.Closure((String) item.get("kind"), (String) item.get("reason")));
        });
        Map<String, OfficePolicy.Checklist> checklists = new LinkedHashMap<>();
        Map<String, Object> listed = (Map<String, Object>) row.get("checklists");
        listed.forEach((service, value) -> {
            Map<String, Object> item = (Map<String, Object>) value;
            List<String> docs = ((List<Object>) item.get("documents")).stream().map(String::valueOf).toList();
            checklists.put(service, new OfficePolicy.Checklist(docs, (String) item.get("instructions"),
                    LocalDate.parse((String) item.get("updatedAt"))));
        });
        return Optional.of(new OfficePolicy(officeId, times, ((Number) row.get("capacity_per_slot")).intValue(), closures, checklists));
    }

    @Override
    public void save(OfficePolicy policy) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("office_id", policy.officeId());
        row.put("slot_times", policy.slotTimes().stream().map(LocalTime::toString).toList());
        row.put("capacity_per_slot", policy.capacityPerSlot());
        Map<String, Object> closures = new LinkedHashMap<>();
        policy.closures().forEach((date, closure) -> closures.put(date.toString(),
                Map.of("kind", closure.kind(), "reason", closure.reason())));
        row.put("closures", closures);
        Map<String, Object> checklists = new LinkedHashMap<>();
        policy.checklists().forEach((service, checklist) -> checklists.put(service,
                Map.of("documents", checklist.documents(), "instructions", checklist.instructions(),
                        "updatedAt", checklist.updatedAt().toString())));
        row.put("checklists", checklists);
        row.put("updated_at", java.time.Instant.now().toString());
        send(request("?on_conflict=office_id").header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(row), StandardCharsets.UTF_8)).build());
    }

    private HttpRequest.Builder request(String query) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + query)).timeout(Duration.ofSeconds(10))
                .header("apikey", key);
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer " + key);
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                throw new IllegalStateException("Office settings storage returned " + response.statusCode() + ".");
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Office settings storage did not answer.", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Office settings storage did not answer.", e);
        }
    }
}
