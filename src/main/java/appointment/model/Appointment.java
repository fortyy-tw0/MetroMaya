package appointment.model;

import appointment.service.BookingException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** A booked slot for one citizen, one service, at one office. */
public class Appointment {

    private final String referenceNo;
    private final Citizen citizen;
    private final GovService service;
    private final Office office;
    private final Instant createdAt;
    private LocalDate date;
    private LocalTime time;
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    public Appointment(String referenceNo, Citizen citizen, GovService service, Office office,
                       LocalDate date, LocalTime time, Instant createdAt) {
        this.referenceNo = referenceNo;
        this.citizen = citizen;
        this.service = service;
        this.office = office;
        this.date = date;
        this.time = time;
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return status == AppointmentStatus.CONFIRMED;
    }

    public void cancel() {
        if (!isActive()) {
            throw new BookingException("This appointment has already been cancelled.");
        }
        status = AppointmentStatus.CANCELLED;
    }

    public void moveTo(LocalDate newDate, LocalTime newTime) {
        if (!isActive()) {
            throw new BookingException("A cancelled appointment cannot be rescheduled.");
        }
        this.date = newDate;
        this.time = newTime;
    }

    public String getReferenceNo() { return referenceNo; }
    public Citizen getCitizen() { return citizen; }
    public GovService getService() { return service; }
    public Office getOffice() { return office; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
