package appointment.model;

import java.util.List;

/** Driver's license transactions: student permit, new license, renewal, duplicate, and so on. */
public class LicenseService extends GovService {

    public LicenseService(String id, String name, String description, double fee,
                          int durationMinutes, int minimumAge, List<String> specificRequirements) {
        super(id, name, description, fee, durationMinutes, minimumAge, specificRequirements);
    }

    @Override
    public ServiceCategory getCategory() {
        return ServiceCategory.DRIVERS_LICENSE;
    }

    @Override
    protected List<String> getCommonRequirements() {
        return List.of("One valid government-issued ID", "Completed license application form");
    }
}
