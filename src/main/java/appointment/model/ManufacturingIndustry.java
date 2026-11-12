package appointment.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Factories, workshops, fabrication and processing. */
public class ManufacturingIndustry extends Industry {

    public ManufacturingIndustry() {
        super("manufacturing", "Manufacturing",
                "Factories, workshops, fabrication and processing.", 3500.00);
    }

    @Override
    public Set<InspectionType> requiredInspections() {
        return new LinkedHashSet<>(List.of(InspectionType.BUILDING,
                InspectionType.FIRE_SAFETY, InspectionType.ENVIRONMENTAL));
    }

    @Override
    public List<String> requiredDocuments() {
        return List.of("Environmental compliance certificate or certificate of non-coverage",
                "Inventory of hazardous materials (if any)");
    }

    @Override
    public List<FeeLine> surcharges() {
        return List.of(new FeeLine("Pollution control fee", 800.00));
    }
}
