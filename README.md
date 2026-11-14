# MetroMaya

> **Skip the line, get on business.**

MetroMaya is a business permit appointment website for Metro Manila. Business owners see their permit fees, inspections and required documents before they go, then book a time slot at their city hall's business permits office. It was built as a course project for Object-Oriented Programming.

**Live site:** [metro-maya.vercel.app](https://metro-maya.vercel.app)

> Fees shown on the site are **sample rates for a course project, not official LGU fees**. City hall addresses, barangays and ZIP codes are real.

---

## Purpose

Applicants usually find out what a business permit costs, which inspections they need and which documents to bring only when they reach the counter, which often means more than one trip. MetroMaya puts that information up front and gives each applicant a confirmed time slot, so one prepared visit is enough.

It covers the 16 cities of Metro Manila and the municipality of Pateros, and three services:

- **New business permit**
- **Annual renewal**
- **Inspection visit**

Maya, a tree sparrow dressed as a mail carrier, is the site's guide and mascot.

---

## How to use it

### 1. Check your fees (home page)

- **Fees:** pick your city and business size to see the base permit fee, inspections and extra charges for each type of business.
- **Fees Estimator:** choose a service, your type of business, number of employees and city to get an estimated total, a fee breakdown, the inspections, the time needed and the documents to bring.
- **Offices:** see your city hall's address and the four desks a permit passes through: the Business Permits and Licensing Office, the Treasurer's Office, the Bureau of Fire Protection desk and the Health Office.

### 2. Book a slot (`/book.html`)

1. **Service:** new permit, renewal or inspection visit.
2. **Business:** business name, Address Line 1, then City, Barangay and ZIP code, your type of business, number of employees, and the owner's contact details. An estimate appears once the city, type of business and employees are filled in.
3. **Office:** your city's business permits office is selected for you, with its address and directions.
4. **Date and time:** a weekday up to 30 days ahead. Slots run hourly from 8:00 to 16:00, with no 12:00 slot.
5. **Review and confirm:** you get a reference number such as `BPL-20261116-ABCDE`.

No account is needed.

### 3. Track a booking (`/manage.html`)

Enter your reference number to see the booking, move it to another free slot, or cancel it.

---

## Tech stack

| Part | Technology |
| --- | --- |
| Backend | **Java 21**, using the JDK's built-in HTTP server (`com.sun.net.httpserver`). No frameworks, no build tool, no external libraries. |
| Frontend | **HTML, CSS and vanilla JavaScript**. Static pages served by the Java server. |
| Fonts | Lexend, Figtree and JetBrains Mono (Google Fonts) |
| Data | Bookings are kept in memory. Barangays come from the PSA's PSGC list and ZIP codes from the PHLPost directory. |
| Mascot assets | Python 3 (NumPy, SciPy, Pillow) scripts in `tools/maya/` generate Maya's animation frames |
| Hosting | **Vercel**, using its container runtime with the root `Dockerfile` |

### Object-oriented design

The Java model is built around three class hierarchies:

| What varies | Design | Classes |
| --- | --- | --- |
| Type of business | Abstract class with subclasses | `Industry` → `RetailIndustry`, `FoodIndustry`, `ManufacturingIndustry`, `ProfessionalServicesIndustry`, `HealthcareIndustry` |
| Business size | Enum with constant-specific methods | `BusinessSize`: `MICRO`, `SMALL`, `MEDIUM`, `LARGE` |
| Service | Abstract class with a final template method `quote()` | `PermitService` → `NewPermitService`, `RenewalService`, `InspectionVisitService` |

Storage goes through the `AppointmentRepository` interface, implemented by `InMemoryAppointmentRepository`. A full walkthrough of the code is in [`docs/MetroMaya-How-the-Code-Works.pdf`](docs/MetroMaya-How-the-Code-Works.pdf).

---

## Project structure

```
src/main/java/appointment/
  Main.java        starts the web server
  model/           industries, business sizes, services, offices, fees, appointments
  service/         booking rules, catalog, slot availability
  repository/      AppointmentRepository and its in-memory implementation
  web/             HTTP server, API routes, static file handler, JSON
src/main/resources/static/
  index.html       home page
  book.html        booking wizard
  manage.html      track, move or cancel a booking
  css/, js/, img/  styles, scripts and Maya's images
tools/             scripts that build Maya's animations and the barangay/ZIP data
docs/              the code guide (PDF)
```

---

## Running it locally

**Requirements:** Java 21 (JDK).

```bash
git clone https://github.com/fortyy-tw0/MetroMaya.git
cd MetroMaya
./run.sh
```

Then open [http://localhost:8080](http://localhost:8080). Set the `PORT` environment variable to use another port.

Bookings are stored in memory, so they reset whenever the server restarts.

---

## API

| Method and path | What it does |
| --- | --- |
| `GET /api/services`, `/api/industries`, `/api/offices`, `/api/config` | Lists services, types of business, city offices and size tiers |
| `GET /api/quote?serviceId=&industryId=&employees=` | Fee estimate, inspections, documents and time |
| `GET /api/slots?officeId=&date=YYYY-MM-DD` | Available time slots for an office and day |
| `POST /api/appointments` | Books a slot and returns the reference number |
| `GET /api/appointments/{ref}` | Looks up a booking |
| `POST /api/appointments/{ref}/cancel` | Cancels a booking |
| `POST /api/appointments/{ref}/reschedule` | Moves a booking to a new date and time |
