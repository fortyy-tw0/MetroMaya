package appointment.model;

import java.util.List;
import java.util.Set;

/**
 * A line of business. Each subclass decides its own base fee, required inspections, documents
 * and any extra regulatory charges, so the rest of the system just asks the industry.
 */
public abstract class Industry {

    private final String id;
    private final String name;
    private final String description;
    private final double baseFee;

    protected Industry(String id, String name, String description, double baseFee) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.baseFee = baseFee;
    }

    /** Inspections this industry always needs, regardless of size. */
    public abstract Set<InspectionType> requiredInspections();

    /** Documents this industry must bring on top of the standard ones. */
    public abstract List<String> requiredDocuments();

    /** Extra regulatory charges for this industry. None by default. */
    public List<FeeLine> surcharges() {
        return List.of();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    /** Permit fee for a micro business. Larger businesses pay a multiple of this. */
    public double getBaseFee() { return baseFee; }
}
