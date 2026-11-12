package appointment.service;

import appointment.model.BusinessSize;
import appointment.model.FoodIndustry;
import appointment.model.HealthcareIndustry;
import appointment.model.Industry;
import appointment.model.InspectionVisitService;
import appointment.model.ManufacturingIndustry;
import appointment.model.NewPermitService;
import appointment.model.Office;
import appointment.model.PermitService;
import appointment.model.ProfessionalServicesIndustry;
import appointment.model.RenewalService;
import appointment.model.RetailIndustry;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Holds the services, industries and offices. Seeded in code; replace with database reads later. */
public class CatalogService {

    private final Map<String, PermitService> services = new LinkedHashMap<>();
    private final Map<String, Industry> industries = new LinkedHashMap<>();
    private final Map<String, Office> offices = new LinkedHashMap<>();

    public CatalogService() {
        add(new NewPermitService());
        add(new RenewalService());
        add(new InspectionVisitService());

        add(new RetailIndustry());
        add(new FoodIndustry());
        add(new ManufacturingIndustry());
        add(new ProfessionalServicesIndustry());
        add(new HealthcareIndustry());

        add(new Office("main", "BPLO Main Office",
                "Ground Floor, City Hall", 4,
                Set.of("new-permit", "renewal", "inspection"),
                EnumSet.allOf(BusinessSize.class)));
        add(new Office("north", "BPLO North District Desk",
                "North District Civic Building, 2nd Floor", 2,
                Set.of("new-permit", "renewal", "inspection"),
                EnumSet.of(BusinessSize.MICRO, BusinessSize.SMALL, BusinessSize.MEDIUM)));
        add(new Office("south", "BPLO South District Desk",
                "South Plaza Government Hub, Ground Floor", 2,
                Set.of("renewal", "inspection"),
                EnumSet.of(BusinessSize.MICRO, BusinessSize.SMALL)));
    }

    private void add(PermitService s) { services.put(s.getId(), s); }
    private void add(Industry i) { industries.put(i.getId(), i); }
    private void add(Office o) { offices.put(o.getId(), o); }

    public List<PermitService> getServices() { return List.copyOf(services.values()); }
    public List<Industry> getIndustries() { return List.copyOf(industries.values()); }
    public List<Office> getOffices() { return List.copyOf(offices.values()); }

    public PermitService getService(String id) {
        PermitService s = id == null ? null : services.get(id);
        if (s == null) throw new NotFoundException("That service does not exist.");
        return s;
    }

    public Industry getIndustry(String id) {
        Industry i = id == null ? null : industries.get(id);
        if (i == null) throw new NotFoundException("That type of business does not exist.");
        return i;
    }

    public Office getOffice(String id) {
        Office o = id == null ? null : offices.get(id);
        if (o == null) throw new NotFoundException("That office does not exist.");
        return o;
    }
}
