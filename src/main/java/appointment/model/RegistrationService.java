package appointment.model;

import java.util.List;

/** Vehicle registration transactions: new registration, annual renewal, transfer of ownership, and so on. */
public class RegistrationService extends GovService {

    public RegistrationService(String id, String name, String description, double fee,
                               int durationMinutes, int minimumAge, List<String> specificRequirements) {
        super(id, name, description, fee, durationMinutes, minimumAge, specificRequirements);
    }

    @Override
    public ServiceCategory getCategory() {
        return ServiceCategory.VEHICLE_REGISTRATION;
    }

    @Override
    protected List<String> getCommonRequirements() {
        return List.of("Valid insurance certificate", "Owner's valid government-issued ID");
    }
}
