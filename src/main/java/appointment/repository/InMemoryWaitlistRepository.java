package appointment.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryWaitlistRepository implements WaitlistRepository {
    private final List<Entry> waiting = new ArrayList<>();

    @Override
    public synchronized void add(String officeId, LocalDate date, String email) {
        boolean already = waiting.stream().anyMatch(e -> e.officeId().equals(officeId) && e.date().equals(date)
                && e.email().equalsIgnoreCase(email));
        if (!already) waiting.add(new Entry(UUID.randomUUID().toString(), officeId, date, email));
    }

    @Override
    public synchronized List<Entry> waiting(String officeId, LocalDate date) {
        return waiting.stream().filter(e -> e.officeId().equals(officeId) && e.date().equals(date)).toList();
    }

    @Override
    public synchronized int countWaiting(String email, LocalDate from) {
        return (int) waiting.stream().filter(e -> e.email().equalsIgnoreCase(email) && !e.date().isBefore(from)).count();
    }

    @Override
    public synchronized void markNotified(String id) {
        waiting.removeIf(e -> e.id().equals(id));
    }

    /** Test helper: everything still waiting. */
    public synchronized List<Entry> all() { return new ArrayList<>(waiting); }
}
