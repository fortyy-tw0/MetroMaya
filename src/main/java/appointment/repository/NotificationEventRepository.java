package appointment.repository;

public interface NotificationEventRepository {
    boolean hasSent(String eventKey);
    void markSent(String eventKey);
}
