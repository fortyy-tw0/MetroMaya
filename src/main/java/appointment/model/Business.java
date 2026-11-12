package appointment.model;

/** The business a permit is for. Its size is worked out from the employee count. */
public class Business {

    public static final int MAX_EMPLOYEES = 100_000;

    private final String name;
    private final String address;
    private final Industry industry;
    private final int employeeCount;

    public Business(String name, String address, Industry industry, int employeeCount) {
        if (name == null || name.trim().length() < 2) {
            throw new IllegalArgumentException("Please enter your business name.");
        }
        if (address == null || address.trim().length() < 5) {
            throw new IllegalArgumentException("Please enter the business address, including the barangay.");
        }
        validate(industry, employeeCount);
        this.name = name.trim();
        this.address = address.trim();
        this.industry = industry;
        this.employeeCount = employeeCount;
    }

    private Business(Industry industry, int employeeCount) {
        validate(industry, employeeCount);
        this.name = "Estimate";
        this.address = "";
        this.industry = industry;
        this.employeeCount = employeeCount;
    }

    /** A nameless business used only to price a quote before anyone books. */
    public static Business forEstimate(Industry industry, int employeeCount) {
        return new Business(industry, employeeCount);
    }

    private static void validate(Industry industry, int employeeCount) {
        if (industry == null) {
            throw new IllegalArgumentException("Please choose your type of business.");
        }
        if (employeeCount < 1 || employeeCount > MAX_EMPLOYEES) {
            throw new IllegalArgumentException("Number of employees must be between 1 and " + MAX_EMPLOYEES + ".");
        }
    }

    public BusinessSize getSize() {
        return BusinessSize.fromEmployees(employeeCount);
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public Industry getIndustry() { return industry; }
    public int getEmployeeCount() { return employeeCount; }
}
