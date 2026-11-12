package appointment.model;

import java.util.List;

/**
 * Size tier, worked out from the number of employees. Each tier carries its own pricing factors
 * and decides which extra inspections and documents it adds. The constants override the abstract
 * methods, so callers never switch on the tier themselves.
 */
public enum BusinessSize {

    MICRO("Micro", 1, 9, 1.00, 1.00) {
        @Override public List<InspectionType> extraInspections() { return List.of(); }
        @Override public List<String> extraDocuments() { return List.of(); }
    },

    SMALL("Small", 10, 99, 1.75, 1.25) {
        @Override public List<InspectionType> extraInspections() { return List.of(); }
        @Override public List<String> extraDocuments() { return List.of(); }
    },

    MEDIUM("Medium", 100, 199, 3.00, 1.50) {
        @Override public List<InspectionType> extraInspections() {
            return List.of(InspectionType.BUILDING);
        }
        @Override public List<String> extraDocuments() {
            return List.of("Latest audited financial statements");
        }
    },

    LARGE("Large", 200, Integer.MAX_VALUE, 5.00, 2.00) {
        @Override public List<InspectionType> extraInspections() {
            return List.of(InspectionType.BUILDING, InspectionType.ENVIRONMENTAL);
        }
        @Override public List<String> extraDocuments() {
            return List.of("Latest audited financial statements", "Certificate of occupancy for the premises");
        }
    };

    private final String label;
    private final int minEmployees;
    private final int maxEmployees;
    private final double feeMultiplier;
    private final double inspectionFactor;

    BusinessSize(String label, int minEmployees, int maxEmployees, double feeMultiplier, double inspectionFactor) {
        this.label = label;
        this.minEmployees = minEmployees;
        this.maxEmployees = maxEmployees;
        this.feeMultiplier = feeMultiplier;
        this.inspectionFactor = inspectionFactor;
    }

    /** Inspections this tier requires on top of the ones its industry requires. */
    public abstract List<InspectionType> extraInspections();

    /** Documents this tier must bring on top of the standard and industry documents. */
    public abstract List<String> extraDocuments();

    public static BusinessSize fromEmployees(int employees) {
        for (BusinessSize size : values()) {
            if (employees >= size.minEmployees && employees <= size.maxEmployees) {
                return size;
            }
        }
        throw new IllegalArgumentException("Number of employees must be at least 1.");
    }

    public String getLabel() { return label; }

    /** Plain-language range, for example "10 to 99 employees". */
    public String getRange() {
        if (maxEmployees == Integer.MAX_VALUE) return minEmployees + " or more employees";
        if (minEmployees == maxEmployees) return minEmployees + " employee";
        return minEmployees + " to " + maxEmployees + " employees";
    }

    /** Multiplies the industry's base permit fee. */
    public double getFeeMultiplier() { return feeMultiplier; }

    /** Multiplies inspection fees and on-site time, because bigger premises take longer to check. */
    public double getInspectionFactor() { return inspectionFactor; }
}
