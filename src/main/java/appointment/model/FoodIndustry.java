package appointment.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Restaurants, bakeries, caterers, food stalls and beverage makers. */
public class FoodIndustry extends Industry {

    public FoodIndustry() {
        super("food", "Food & beverage",
                "Restaurants, bakeries, caterers and food stalls.", 2200.00);
    }

    @Override
    public Set<InspectionType> requiredInspections() {
        return new LinkedHashSet<>(List.of(InspectionType.SANITARY, InspectionType.FIRE_SAFETY));
    }

    @Override
    public List<String> requiredDocuments() {
        return List.of("Health certificates for all food handlers",
                "Water potability test result");
    }

    @Override
    public List<FeeLine> surcharges() {
        return List.of(new FeeLine("Sanitary permit fee", 350.00));
    }
}
