# BPLO Online – Business Permits and Licensing Office appointment system

A web app where business owners book slots for **new permit applications**, **annual renewals** and **inspection visits**. The backend is plain Java 21 (`com.sun.net.httpserver`, no dependencies). The front-end is static HTML, CSS and vanilla JS.

Fees are sample rates for a course project, not official LGU fees.

## Run

```bash
./run.sh            # http://localhost:8080  (set PORT to change)
./run-tests.sh      # 18 plain-Java checks
```

## Polymorphism

| Varies by | Design | Classes |
|---|---|---|
| Industry | abstract class, one subclass each | `Industry` → `RetailIndustry`, `FoodIndustry`, `ManufacturingIndustry`, `ProfessionalServicesIndustry`, `HealthcareIndustry` (base fee, required inspections, documents, surcharges) |
| Business size | enum with constant-specific method bodies | `BusinessSize` MICRO / SMALL / MEDIUM / LARGE (fee multiplier, inspection cost factor, extra inspections and documents) |
| Service type | abstract class with a final template method `quote()` | `PermitService` → `NewPermitService`, `RenewalService`, `InspectionVisitService` |

Other pieces: `Office` (offers some services, serves some sizes, per-slot capacity), `Appointment` (stores a quote snapshot), `AppointmentRepository` interface with an in-memory implementation, and `AppointmentService` holding the booking rules.

## Booking rules

Weekdays only, up to 30 days ahead, hourly slots 8:00–16:00 (no 12:00), per-office capacity, one active booking per email + business + service. References look like `BPL-20261116-ABCDE`.

## API

`GET /api/health`, `/api/config`, `/api/services`, `/api/industries`, `/api/offices`, `/api/quote?serviceId=&industryId=&employees=`, `/api/slots?officeId=&date=`
`POST /api/appointments`, `GET /api/appointments/{ref}`, `POST /api/appointments/{ref}/cancel`, `POST /api/appointments/{ref}/reschedule`

## Deploy on Vercel

Vercel has no Java runtime, so the project uses the Container runtime with the root `Dockerfile` (`Dockerfile.vercel` is a copy). The Vercel project name must not contain underscores. Bookings are kept in memory and reset when the container scales down; swap in a database-backed `AppointmentRepository` to persist them.
