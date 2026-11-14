package appointment.repository;

import appointment.model.StaffAccount;
import java.util.List;
import java.util.Optional;

public interface StaffAccountRepository {
    Optional<StaffAccount> find(String username);
    List<StaffAccount> all();
    void save(StaffAccount account);
}
