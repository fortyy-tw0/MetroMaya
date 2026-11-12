package appointment.model;

import java.util.List;

/**
 * Everything a business owner needs to know before booking: the fee, the inspections that apply,
 * the documents to bring and how long the visit takes.
 */
public record Quote(BusinessSize size,
                    FeeBreakdown fee,
                    List<InspectionType> inspections,
                    List<String> requirements,
                    int estimatedMinutes) {
}
