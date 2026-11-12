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

    public boolean isActive() {
        return status == AppointmentStatus.CONFIRMED;
    }

    public void cancel() {
        if (!isActive()) {
            throw new BookingException("This booking has already been cancelled.");
        }
        status = AppointmentStatus.CANCELLED;
    }

    public void moveTo(LocalDate newDate, LocalTime newTime) {
        if (!isActive()) {
            throw new BookingException("A cancelled booking cannot be moved.");
        }
        this.date = newDate;
        this.time = newTime;
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
}
