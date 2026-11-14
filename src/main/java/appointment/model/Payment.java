package appointment.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A DEMO payment of a booking's fees: nothing is charged and no money moves. It walks through the steps a real
 * LGU e-payment would (choose a channel, scan or open the checkout, pay, get an acknowledgement receipt) so the
 * flow can be shown in a pitch. In production this would be the LGU treasurer's payment gateway.
 */
public class Payment {

    public enum Status { PENDING, PAID }

    private final String transactionId;
    private final String channel;
    private final double amount;
    private final Instant createdAt;
    private Status status;
    private String receiptNo;
    private Instant paidAt;

    public Payment(String transactionId, String channel, double amount, Instant createdAt) {
        this(transactionId, channel, amount, createdAt, Status.PENDING, null, null);
    }

    private Payment(String transactionId, String channel, double amount, Instant createdAt,
                    Status status, String receiptNo, Instant paidAt) {
        this.transactionId = transactionId;
        this.channel = channel;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = status;
        this.receiptNo = receiptNo;
        this.paidAt = paidAt;
    }

    public void markPaid(String receiptNo, Instant paidAt) {
        if (status == Status.PAID) return;
        this.status = Status.PAID;
        this.receiptNo = receiptNo;
        this.paidAt = paidAt;
    }

    public boolean isPaid() { return status == Status.PAID; }
    public String getTransactionId() { return transactionId; }
    public String getChannel() { return channel; }
    public double getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public String getReceiptNo() { return receiptNo; }
    public Instant getPaidAt() { return paidAt; }

    /** Stored as one JSON object on the booking. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txn", transactionId);
        m.put("channel", channel);
        m.put("amount", amount);
        m.put("status", status.name());
        m.put("createdAt", createdAt.toString());
        if (receiptNo != null) m.put("receiptNo", receiptNo);
        if (paidAt != null) m.put("paidAt", paidAt.toString());
        return m;
    }

    public static Payment fromMap(Map<String, Object> m) {
        if (m == null || m.get("txn") == null) return null;
        Object paid = m.get("paidAt");
        return new Payment(String.valueOf(m.get("txn")), String.valueOf(m.get("channel")),
                ((Number) m.get("amount")).doubleValue(), Instant.parse(String.valueOf(m.get("createdAt"))),
                Status.valueOf(String.valueOf(m.get("status"))),
                m.get("receiptNo") == null ? null : String.valueOf(m.get("receiptNo")),
                paid == null ? null : Instant.parse(String.valueOf(paid)));
    }
}
