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

        // One business permits office per Metro Manila city (and Pateros), at its city/municipal hall.
        // Addresses come from the cities' own sites, DILG-NCR and news reports (Nov 2026); the id matches
        // the front end's city id (NCR_CITIES in common.js). Slot capacities are sample values.
        Set<String> all = Set.of("new-permit", "renewal", "inspection");
        add(new Office("caloocan", "Caloocan Business Permits and Licensing Office",
                "Caloocan City Hall, 8th Avenue cor. 8th Street, Grace Park East, Caloocan City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("las-pinas", "Las Piñas Business Permits and Licensing Office",
                "Las Piñas City Hall Compound, Alabang–Zapote Road, Pamplona Tres, Las Piñas City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("makati", "Makati Business Permits Office",
                "Ground Floor, Makati City Hall Main Building II, J.P. Rizal Street, Poblacion, Makati City 1200", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("malabon", "Malabon Business Permit and Licensing Office",
                "3rd Floor, Malabon City Hall, F. Sevilla Boulevard, San Agustin, Malabon City", 3, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("mandaluyong", "Mandaluyong Business Permits and Licensing Department",
                "Mandaluyong City Hall, Maysilo Circle, Plainview, Mandaluyong City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("manila", "Manila Bureau of Permits",
                "Manila City Hall, Antonio Villegas Street, Ermita, Manila 1000", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("marikina", "Marikina Business Permits and Licensing Office",
                "Marikina City Hall, Shoe Avenue, Santa Elena, Marikina City 1800", 3, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("muntinlupa", "Muntinlupa Business Permits and Licensing Office",
                "Muntinlupa City Hall Compound, Putatan, Muntinlupa City", 3, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("navotas", "Navotas Business Permits and Licensing Office",
                "Navotas City Hall, M. Naval Street, Sipac-Almacen, Navotas City 1485", 3, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("paranaque", "Parañaque Business Permits and Licensing Office",
                "Parañaque City Hall, San Antonio Avenue, San Antonio, Parañaque City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("pasay", "Pasay Business Permits and Licensing Office",
                "Pasay City Hall, F.B. Harrison Street, Pasay City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("pasig", "Pasig Business Permits and Licensing Office",
                "Pasig Temporary City Hall, Eulogio Amang Rodriguez Avenue, Rosario, Pasig City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("pateros", "Pateros Business Permits and Licensing Office",
                "Pateros Municipal Hall, G. de Borja Street, Aguho, Pateros", 2, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("quezon-city", "Quezon City Business Permits and Licensing Department",
                "Ground Floor, Civic Center Building A, Quezon City Hall Compound, Elliptical Road, Central, Diliman, Quezon City 1100", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("san-juan", "San Juan Business Permits and Licensing Office",
                "San Juan City Hall (City Government Center), Pinaglabanan Street, San Juan City", 3, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("taguig", "Taguig Business Permits and Licensing Office",
                "Taguig City Hall Compound, General Luna Street, Tuktukan, Taguig City", 4, all, EnumSet.allOf(BusinessSize.class)));
        add(new Office("valenzuela", "Valenzuela Business Permits and Licensing Office",
                "3rd Floor, Bulwagang Geronimo Angeles (Finance Center), Valenzuela City Government Complex, MacArthur Highway, Karuhatan, Valenzuela City", 4, all, EnumSet.allOf(BusinessSize.class)));
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
