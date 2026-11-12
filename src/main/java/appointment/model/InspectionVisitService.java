package appointment.model;

import java.util.List;

/** Books an inspection team to visit the premises. No permit fee, only the inspections themselves. */
public class InspectionVisitService extends PermitService {

    public InspectionVisitService() {
        super("inspection", "Inspection visit",
                "Schedule the inspectors to check your premises.");
    }

    @Override
    public List<String> baseDocuments() {
        return List.of("A representative of the business with a valid ID",
                "Copy of your permit or application receipt",
                "Floor plan or sketch of the premises");
    }

    @Override
    protected List<String> extraDocumentsFor(Business business) {
        return List.of();
    }

    @Override
    public List<InspectionType> inspectionsFor(Business business) {
        return allInspections(business);
    }

    @Override
    public FeeBreakdown computeFee(Business business) {
        FeeBreakdown fee = new FeeBreakdown();
        addInspectionLines(fee, inspectionsFor(business), business.getSize());
        return fee.add("Inspector dispatch fee", 100.00);
    }

    /** The team checks everything in one visit, so the time adds up. */
    @Override
    public int estimatedMinutes(Business business) {
        int total = inspectionsFor(business).stream().mapToInt(InspectionType::getMinutes).sum();
        return scaledMinutes(total, business.getSize());
    }
}
