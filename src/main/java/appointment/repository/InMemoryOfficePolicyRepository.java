package appointment.repository;

import appointment.model.OfficePolicy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOfficePolicyRepository implements OfficePolicyRepository {
    private final Map<String, OfficePolicy> policies = new ConcurrentHashMap<>();
    @Override public Optional<OfficePolicy> find(String officeId) { return Optional.ofNullable(policies.get(officeId)); }
    @Override public void save(OfficePolicy policy) { policies.put(policy.officeId(), policy); }
}
