package appointment.repository;

import appointment.model.OfficePolicy;
import java.util.Optional;

public interface OfficePolicyRepository {
    Optional<OfficePolicy> find(String officeId);
    void save(OfficePolicy policy);
}
