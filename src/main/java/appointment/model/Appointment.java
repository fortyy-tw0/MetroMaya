package appointment.model;

import appointment.service.BookingException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One booked slot. The quote (fee, inspections, documents) is saved at booking time,
 * so later price changes never alter an existing booking.
 */
public class Appointment {

    private final String referenceNo;
    private final Applicant applicant;
    private final Business business;
    private final PermitService service;
    private final Office office;
    private final Quote quote;
    private final Instant createdAt;
    private LocalDate date;
    private LocalTime time;
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;
    /** The demo fee payment, or null when the fees haven't been paid online. */
    private Payment payment;
    /** When office staff accepted the applicant's booking at the counter. */
    private Instant acceptedAt;
    /** Secret management credential is stored only as a hash. Null on bookings made before this feature. */
    private String managementTokenHash;
    /** Opaque office lookup code. It grants no access without a scoped staff login. */
    private String checkInCode;
    /** Staff-arrival sequence, unique within the office and appointment date. */
    private Integer queueNumber;
    private boolean walkIn;
    private Instant noShowAt;
    private boolean demoRecord;
    private CounterPayment counterPayment;
    private String managementTokenCiphertext;

    public Appointment(String referenceNo, Applicant applicant, Business business, PermitService service,
                       Office office, LocalDate date, LocalTime time, Quote quote, Instant createdAt) {
        this.referenceNo = referenceNo;
        this.applicant = applicant;
        this.business = business;
        this.service = service;
        this.office = office;
        this.date = date;
        this.time = time;
        this.quote = quote;
        this.createdAt = createdAt;
    }

    /** Rebuilds a stored appointment with the status it was saved with (used by database repositories). */
    public static Appointment restore(String referenceNo, Applicant applicant, Business business, PermitService service,
                                      Office office, LocalDate date, LocalTime time, Quote quote, Instant createdAt,
                                      AppointmentStatus status) {
        Appointment a = new Appointment(referenceNo, applicant, business, service, office, date, time, quote, createdAt);
        a.status = status;
        return a;
    }

    public boolean isActive() {
        return status == AppointmentStatus.CONFIRMED;
    }

    public void cancel() {
        if (!isActive()) {
            throw new BookingException("This booking is no longer active.");
        }
        if (acceptedAt != null) throw new BookingException("This booking was accepted at the office and cannot be cancelled online.");
        status = AppointmentStatus.CANCELLED;
    }

    public void moveTo(LocalDate newDate, LocalTime newTime) {
        if (!isActive()) {
            throw new BookingException("A cancelled booking cannot be moved.");
        }
        if (acceptedAt != null) throw new BookingException("This booking was accepted at the office and cannot be moved online.");
        this.date = newDate;
        this.time = newTime;
    }

    public void accept(Instant when) {
        if (!isActive()) throw new BookingException("A cancelled booking cannot be accepted.");
        if (acceptedAt == null) acceptedAt = when;
    }

    public void markNoShow(Instant when) {
        if (!isActive() || acceptedAt != null || walkIn)
            throw new BookingException("Only an unaccepted scheduled booking can be marked as a no-show.");
        status = AppointmentStatus.NO_SHOW;
        noShowAt = when;
    }

    public String getReferenceNo() { return referenceNo; }
    public Applicant getApplicant() { return applicant; }
    public Business getBusiness() { return business; }
    public PermitService getService() { return service; }
    public Office getOffice() { return office; }
    public Quote getQuote() { return quote; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public String getManagementTokenHash() { return managementTokenHash; }
    public void setManagementTokenHash(String managementTokenHash) { this.managementTokenHash = managementTokenHash; }
    public String getCheckInCode() { return checkInCode; }
    public void setCheckInCode(String checkInCode) { this.checkInCode = checkInCode; }
    public Integer getQueueNumber() { return queueNumber; }
    public void setQueueNumber(Integer queueNumber) { this.queueNumber = queueNumber; }
    public boolean isWalkIn() { return walkIn; }
    public void setWalkIn(boolean walkIn) { this.walkIn = walkIn; }
    public Instant getNoShowAt() { return noShowAt; }
    public void setNoShowAt(Instant noShowAt) { this.noShowAt = noShowAt; }
    public boolean isDemoRecord() { return demoRecord; }
    public void setDemoRecord(boolean demoRecord) { this.demoRecord = demoRecord; }
    public CounterPayment getCounterPayment() { return counterPayment; }
    public void setCounterPayment(CounterPayment counterPayment) { this.counterPayment = counterPayment; }
    public String getManagementTokenCiphertext() { return managementTokenCiphertext; }
    public void setManagementTokenCiphertext(String managementTokenCiphertext) { this.managementTokenCiphertext = managementTokenCiphertext; }
}
