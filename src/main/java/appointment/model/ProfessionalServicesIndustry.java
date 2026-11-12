package appointment.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Offices, consultancies, salons, repair shops and other service providers. */
public class ProfessionalServicesIndustry extends Industry {

    public ProfessionalServicesIndustry() {
        super("services", "Professional & personal services",
                "Offices, consultancies, salons and repair shops.", 1200.00);
    }

    @Override
    public Set<InspectionType> requiredInspections() {
        return new LinkedHashSet<>(List.of(InspectionType.FIRE_SAFETY));
    }

    @Override
    public List<String> requiredDocuments() {
        return List.of("Professional licence or ID of the practitioner (if regulated)");
    }
}
