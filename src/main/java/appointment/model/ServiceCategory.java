package appointment.model;

/** The two families of services this system books appointments for. */
public enum ServiceCategory {
    DRIVERS_LICENSE("Driver's License"),
    VEHICLE_REGISTRATION("Vehicle Registration");

    private final String label;

    ServiceCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
