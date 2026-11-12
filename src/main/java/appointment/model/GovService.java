package appointment.model;

import java.util.List;

/**
 * A government service a citizen can book an appointment for.
 * Subclasses decide their category and which documents are always required.
 */
public abstract class GovService {

    private final String id;
    private final String name;
    private final String description;
    private final double fee;
    private final int durationMinutes;
    private final int minimumAge;
    private final List<String> specificRequirements;

    protected GovService(String id, String name, String description, double fee,
                         int durationMinutes, int minimumAge, List<String> specificRequirements) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.fee = fee;
        this.durationMinutes = durationMinutes;
        this.minimumAge = minimumAge;
        this.specificRequirements = List.copyOf(specificRequirements);
    }

    public abstract ServiceCategory getCategory();

    /** Documents required for every service of this category. */
    protected abstract List<String> getCommonRequirements();

    /** Full checklist shown to the citizen: category-wide documents first, then service-specific ones. */
    public List<String> getRequirements() {
        List<String> all = new java.util.ArrayList<>(getCommonRequirements());
        all.addAll(specificRequirements);
        return List.copyOf(all);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getFee() { return fee; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getMinimumAge() { return minimumAge; }
}
