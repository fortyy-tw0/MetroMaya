package appointment.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Something a business can book a slot for. Each subclass works out its own fee, inspections,
 * documents and visit length for a given business (polymorphism), while {@link #quote} stays
 * the same for all of them.
 */
public abstract class PermitService {

    private final String id;
    private final String name;
    private final String description;

    protected PermitService(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    /** Documents everyone using this service must bring. */
    public abstract List<String> baseDocuments();

    /** Extra documents that depend on the business. */
    protected abstract List<String> extraDocumentsFor(Business business);

    public abstract List<InspectionType> inspectionsFor(Business business);

    public abstract FeeBreakdown computeFee(Business business);

    /** Time the owner should expect to spend at the counter or with the inspectors. */
    public abstract int estimatedMinutes(Business business);

    public final List<String> requirementsFor(Business business) {
        List<String> all = new ArrayList<>(baseDocuments());
        all.addAll(extraDocumentsFor(business));
        return List.copyOf(all);
    }

    public final Quote quote(Business business) {
        return new Quote(business.getSize(), computeFee(business), inspectionsFor(business),
                requirementsFor(business), estimatedMinutes(business));
    }

    // ---- helpers shared by the subclasses ----

    /** Every inspection the business needs: its industry's, then its size tier's, without repeats. */
    protected static List<InspectionType> allInspections(Business business) {
        Set<InspectionType> set = new LinkedHashSet<>(business.getIndustry().requiredInspections());
        set.addAll(business.getSize().extraInspections());
        return List.copyOf(set);
    }

    /** Inspection fees grow with the size of the premises. */
    protected static void addInspectionLines(FeeBreakdown fee, List<InspectionType> inspections, BusinessSize size) {
        for (InspectionType type : inspections) {
            fee.add(type.getLabel(), type.getFee() * size.getInspectionFactor());
        }
    }

    protected static int scaledMinutes(int baseMinutes, BusinessSize size) {
        return (int) Math.round(baseMinutes * size.getInspectionFactor());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
