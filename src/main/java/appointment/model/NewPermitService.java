package appointment.model;

import java.util.ArrayList;
import java.util.List;

/** First-time application for a mayor's permit. Needs every inspection the business is subject to. */
public class NewPermitService extends PermitService {

    public NewPermitService() {
        super("new-permit", "New business permit",
                "Apply for a permit to open a new business in the city.");
    }

    @Override
    public List<String> baseDocuments() {
        return List.of("DTI, SEC or CDA registration certificate",
                "Barangay business clearance",
                "Lease contract or land title for the business location",
                "Community tax certificate (cedula)",
                "Zoning or location clearance");
    }

    @Override
    protected List<String> extraDocumentsFor(Business business) {
        List<String> docs = new ArrayList<>(business.getIndustry().requiredDocuments());
        docs.addAll(business.getSize().extraDocuments());
        return docs;
    }

    @Override
    public List<InspectionType> inspectionsFor(Business business) {
        return allInspections(business);
    }

    @Override
    public FeeBreakdown computeFee(Business business) {
        BusinessSize size = business.getSize();
        FeeBreakdown fee = new FeeBreakdown()
                .add("Business permit fee (" + size.getLabel().toLowerCase() + ")",
                        business.getIndustry().getBaseFee() * size.getFeeMultiplier())
                .addAll(business.getIndustry().surcharges());
        addInspectionLines(fee, inspectionsFor(business), size);
        return fee.add("Filing and processing fee", 150.00);
    }

    @Override
    public int estimatedMinutes(Business business) {
        return scaledMinutes(30, business.getSize());
    }
}
