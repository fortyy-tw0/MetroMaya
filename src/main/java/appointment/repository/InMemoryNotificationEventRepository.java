package appointment.repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNotificationEventRepository implements NotificationEventRepository {
    private final Set<String> sent = ConcurrentHashMap.newKeySet();
    @Override public boolean hasSent(String eventKey) { return sent.contains(eventKey); }
    @Override public void markSent(String eventKey) { sent.add(eventKey); }
}
