package appointment.model;

/** The kinds of on-site inspection an LGU can require before it issues or renews a business permit. */
public enum InspectionType {
    SANITARY("Sanitary & health inspection", 250.00, 45, true),
    FIRE_SAFETY("Fire safety inspection", 400.00, 45, true),
    BUILDING("Building & occupancy inspection", 350.00, 60, false),
    ENVIRONMENTAL("Environmental compliance inspection", 600.00, 90, true);

    private final String label;
    private final double fee;
    private final int minutes;
    private final boolean recurringOnRenewal;

    InspectionType(String label, double fee, int minutes, boolean recurringOnRenewal) {
        this.label = label;
        this.fee = fee;
        this.minutes = minutes;
        this.recurringOnRenewal = recurringOnRenewal;
    }

    public String getLabel() { return label; }

    /** Base fee for a micro business. Larger businesses pay more (see {@link BusinessSize#getInspectionFactor()}). */
    public double getFee() { return fee; }

    /** Base on-site time for a micro business. */
    public int getMinutes() { return minutes; }

    /** True if this inspection is repeated every year at renewal. */
    public boolean isRecurringOnRenewal() { return recurringOnRenewal; }
}
