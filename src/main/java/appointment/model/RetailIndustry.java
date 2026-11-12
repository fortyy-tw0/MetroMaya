package appointment.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stores, trading and wholesale. */
public class RetailIndustry extends Industry {

    public RetailIndustry() {
        super("retail", "Retail & trading",
                "Stores, groceries, trading and wholesale.", 1500.00);
    }

    @Override
    public Set<InspectionType> requiredInspections() {
        return new LinkedHashSet<>(List.of(InspectionType.FIRE_SAFETY, InspectionType.BUILDING));
    }

    @Override
    public List<String> requiredDocuments() {
        return List.of("Weights and measures calibration certificate (if you use scales)");
    }
}
