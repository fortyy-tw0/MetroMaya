# Licensing & Registration Appointment System

A web app for booking **driver's license** and **vehicle registration** appointments.
Java backend (JDK only, no libraries) plus a plain HTML/CSS/JavaScript front-end.

**Flow:** landing page → *Book an Appointment* → service → office → date & time → your details → review → reference number.
Citizens can look up, reschedule or cancel with that reference number.

## Run it locally

Needs JDK 17 or newer.

```bash
./run.sh            # then open http://localhost:8080
./run.sh 3000       # or pick another port
./run-tests.sh      # runs the booking-rule checks
```

## OOP design

| Class | Role |
|---|---|
| `GovService` (abstract) | A bookable service: name, fee, duration, minimum age, requirements |
| `LicenseService`, `RegistrationService` | Subclasses. Each sets its category and its own always-required documents (polymorphism) |
| `Office` | A branch, with the services it offers and a per-slot capacity |
| `Citizen` | Validates its own name, email, phone and birth date |
| `Appointment` | One booking. Knows how to `cancel()` and `moveTo()` |
| `AppointmentRepository` (interface) | Storage contract |
| `InMemoryAppointmentRepository` | Current implementation. Swap for a database one without touching the service |
| `CatalogService` | The seeded list of services and offices |
| `AppointmentService` | All booking rules (see below) |
| `ApiHandler`, `StaticHandler`, `WebServer` | Thin HTTP layer on the JDK's built-in server |

### Booking rules
- Weekdays only, from today up to 30 days ahead
- Hourly slots 8:00 to 16:00, no 12:00 (lunch)
- Each office has a capacity per slot; full slots can't be booked
- Each service has a minimum age
- An office can only be booked for services it offers
- One active appointment per person per service

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/config` | Today's date and booking window |
| GET | `/api/services` | All services and required documents |
| GET | `/api/offices` | All offices |
| GET | `/api/slots?officeId=&date=` | Slot availability for a day |
| POST | `/api/appointments` | Book (form-encoded) |
| GET | `/api/appointments/{ref}` | Look up |
| POST | `/api/appointments/{ref}/cancel` | Cancel |
| POST | `/api/appointments/{ref}/reschedule` | Move to a new `date` and `time` |

## Deploy on Vercel

Vercel has no native Java runtime, so this project uses its **Container runtime** (Beta): the root `Dockerfile`
builds the Java app and Vercel serves it. Import this repository in Vercel and deploy; no extra settings are needed.

The `PORT` environment variable defaults to `80` in the Dockerfile. Set `APP_TIMEZONE` (default `Asia/Manila`) to change the time zone used for "today".

> **Note:** bookings are stored in memory, so they reset whenever the container restarts or scales down after idling.
> To keep them, implement `AppointmentRepository` against a database.
