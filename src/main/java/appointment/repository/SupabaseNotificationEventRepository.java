package appointment.repository;

import appointment.util.Json;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class SupabaseNotificationEventRepository implements NotificationEventRepository {
    private final String url;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseNotificationEventRepository(String projectUrl, String key) {
        url = projectUrl.trim().replaceAll("/+$", "").replaceAll("/rest/v1$", "") + "/rest/v1/notification_events";
        this.key = key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasSent(String eventKey) {
        String query = "?select=event_key&event_key=eq." + URLEncoder.encode(eventKey, StandardCharsets.UTF_8) + "&limit=1";
        return !((List<Object>) Json.parse(send(request(query).GET().build()).body())).isEmpty();
    }

    @Override
    public void markSent(String eventKey) {
        HttpRequest request = request("?on_conflict=event_key")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(Map.of("event_key", eventKey)))).build();
        send(request);
    }

    private HttpRequest.Builder request(String query) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url + query)).timeout(Duration.ofSeconds(10))
                .header("apikey", key);
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer " + key);
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> result = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() / 100 != 2)
                throw new IllegalStateException("Notification log returned " + result.statusCode() + ".");
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Notification log did not answer.", e);
        } catch (java.io.IOException e) { throw new IllegalStateException("Notification log did not answer.", e); }
    }
}
