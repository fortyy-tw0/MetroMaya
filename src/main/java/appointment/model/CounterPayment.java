package appointment.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** A city hall payment verified against an official treasurer receipt, separate from demo online checkout. */
public record CounterPayment(double amount, String officialReceiptNo, Instant recordedAt, String recordedBy) {
    public CounterPayment {
        if (!Double.isFinite(amount) || amount <= 0 || amount > 10_000_000)
            throw new IllegalArgumentException("Enter a valid paid amount.");
        if (officialReceiptNo == null || !officialReceiptNo.matches("[A-Za-z0-9][A-Za-z0-9 ._/-]{3,59}"))
            throw new IllegalArgumentException("Enter a valid official receipt number (4–60 characters).");
        if (recordedAt == null || recordedBy == null || recordedBy.isBlank())
            throw new IllegalArgumentException("A treasurer and time are required to verify payment.");
        officialReceiptNo = officialReceiptNo.trim();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("amount", amount);
        out.put("officialReceiptNo", officialReceiptNo);
        out.put("recordedAt", recordedAt.toString());
        out.put("recordedBy", recordedBy);
        out.put("source", "CITY_HALL");
        out.put("verified", true);
        return out;
    }

    public static CounterPayment fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new CounterPayment(((Number) map.get("amount")).doubleValue(),
                (String) map.get("officialReceiptNo"), Instant.parse((String) map.get("recordedAt")),
                (String) map.get("recordedBy"));
    }
}
