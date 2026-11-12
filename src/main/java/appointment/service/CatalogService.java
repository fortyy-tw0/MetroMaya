package appointment.service;

import appointment.model.GovService;
import appointment.model.LicenseService;
import appointment.model.Office;
import appointment.model.RegistrationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Holds the list of services and offices. Seeded in code; replace with database reads later. */
public class CatalogService {

    private final Map<String, GovService> services = new LinkedHashMap<>();
    private final Map<String, Office> offices = new LinkedHashMap<>();

    public CatalogService() {
        seedServices();
        seedOffices();
    }

    private void seedServices() {
        add(new LicenseService("lic-student", "Student Permit",
                "First step to driving legally. Valid for supervised practice.",
                317.50, 30, 16,
                List.of("Medical certificate from an accredited clinic",
                        "Parent or guardian consent (if under 18)")));
        add(new LicenseService("lic-new", "New Driver's License",
                "Convert your student permit into a full non-professional license.",
                585.00, 45, 17,
                List.of("Student permit held for the minimum required period",
                        "Medical certificate from an accredited clinic",
                        "Proof of completed driving course")));
        add(new LicenseService("lic-renew", "License Renewal",
                "Renew a license that is expiring or recently expired.",
                820.00, 20, 18,
                List.of("Current driver's license",
                        "Medical certificate from an accredited clinic")));
        add(new LicenseService("lic-dup", "Duplicate / Replacement License",
                "Replace a lost, stolen or damaged license.",
                680.00, 20, 18,
                List.of("Affidavit of loss (if lost or stolen)",
                        "Damaged license (if damaged)")));

        add(new RegistrationService("reg-new", "New Vehicle Registration",
                "Register a brand-new or newly imported vehicle for the first time.",
                1250.00, 60, 18,
                List.of("Sales invoice or deed of sale",
                        "Vehicle inspection report",
                        "Emission test result")));
        add(new RegistrationService("reg-renew", "Annual Registration Renewal",
                "Renew the yearly registration of your vehicle.",
                900.00, 30, 18,
                List.of("Previous certificate of registration",
                        "Emission test result")));
        add(new RegistrationService("reg-transfer", "Transfer of Ownership",
                "Move a registered vehicle to a new owner's name.",
                1100.00, 45, 18,
                List.of("Notarized deed of sale",
                        "Previous certificate of registration",
                        "Buyer's and seller's valid IDs")));
    }

    private void seedOffices() {
        Set<String> all = Set.copyOf(services.keySet());
        add(new Office("central", "Central Licensing & Registration Office",
                "Government Center Complex, Main Avenue", 3, all));
        add(new Office("north", "North District Branch",
                "North District Civic Building, 2nd Floor", 2,
                Set.of("lic-student", "lic-new", "lic-renew", "lic-dup", "reg-renew")));
        add(new Office("south", "South District Branch",
                "South Plaza Government Hub, Ground Floor", 2,
                Set.of("lic-renew", "lic-dup", "reg-renew", "reg-transfer")));
    }

    private void add(GovService s) { services.put(s.getId(), s); }
    private void add(Office o) { offices.put(o.getId(), o); }

    public List<GovService> getServices() {
        return List.copyOf(services.values());
    }

    public List<Office> getOffices() {
        return List.copyOf(offices.values());
    }

    public GovService getService(String id) {
        GovService s = id == null ? null : services.get(id);
        if (s == null) throw new NotFoundException("That service does not exist.");
        return s;
    }

    public Office getOffice(String id) {
        Office o = id == null ? null : offices.get(id);
        if (o == null) throw new NotFoundException("That office does not exist.");
        return o;
    }
}
