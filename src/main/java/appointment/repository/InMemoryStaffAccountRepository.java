package appointment.repository;

import appointment.model.StaffAccount;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStaffAccountRepository implements StaffAccountRepository {
    private final Map<String, StaffAccount> accounts = new ConcurrentHashMap<>();

    @Override public Optional<StaffAccount> find(String username) {
        return Optional.ofNullable(accounts.get(username));
    }

    @Override public List<StaffAccount> all() { return List.copyOf(accounts.values()); }

    @Override public void save(StaffAccount account) { accounts.put(account.username(), account); }
}
