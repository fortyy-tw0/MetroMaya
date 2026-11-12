package appointment.model;

import java.util.List;

/**
 * Yearly renewal of an existing permit. Cheaper than a new permit, and only the inspections
 * that repeat every year are charged again.
 */
public class RenewalService extends PermitService {

    private static final double RENEWAL_DISCOUNT = 0.80;

    public RenewalService() {
        super("renewal", "Annual renewal",
                "Renew your business permit for the new year.");
    }

    @Override
    public List<String> baseDocuments() {
        return List.of("Last year's mayor's permit",
                "Renewed barangay business clearance",
                "Sworn statement of gross sales or receipts for last year",
                "Community tax certificate (cedula)");
    }

    @Override
    protected List<String> extraDocumentsFor(Business business) {
        return business.getSize().extraDocuments();
    }

    @Override
    public List<InspectionType> inspectionsFor(Business business) {
        return allInspections(business).stream()
                .filter(InspectionType::isRecurringOnRenewal)
                .toList();
    }

    @Override
    public FeeBreakdown computeFee(Business business) {
        BusinessSize size = business.getSize();
        FeeBreakdown fee = new FeeBreakdown()
                .add("Annual renewal fee (" + size.getLabel().toLowerCase() + ")",
                        business.getIndustry().getBaseFee() * RENEWAL_DISCOUNT * size.getFeeMultiplier())
                .addAll(business.getIndustry().surcharges());
        addInspectionLines(fee, inspectionsFor(business), size);
        return fee.add("Filing and processing fee", 100.00);
    }

    @Override
    public int estimatedMinutes(Business business) {
        return scaledMinutes(20, business.getSize());
    }
}
