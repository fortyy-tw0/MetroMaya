package appointment.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Clinics, pharmacies, laboratories and other health facilities. */
public class HealthcareIndustry extends Industry {

    public HealthcareIndustry() {
        super("healthcare", "Healthcare",
                "Clinics, pharmacies and laboratories.", 3000.00);
    }

    @Override
    public Set<InspectionType> requiredInspections() {
        return new LinkedHashSet<>(List.of(InspectionType.SANITARY,
                InspectionType.FIRE_SAFETY, InspectionType.BUILDING));
    }

    @Override
    public List<String> requiredDocuments() {
        return List.of("Health department licence to operate",
                "Medical waste disposal contract");
    }

    @Override
    public List<FeeLine> surcharges() {
        return List.of(new FeeLine("Health facility regulatory fee", 700.00));
    }
}
