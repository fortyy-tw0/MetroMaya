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
4. **Date and time:** a weekday up to 3 months ahead, except regular holidays. Slots run hourly from 8:00 to 16:00, with no 12:00 slot.
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
| Database | **Supabase (PostgreSQL)**, reached through its REST API with Java's built-in HTTP client. Without Supabase settings, bookings are kept in memory for local testing. |
| Data | Barangays come from the PSA's PSGC list and ZIP codes from the PHLPost directory. Regular holidays follow RA 9492 and the yearly proclamations. |
| Mascot assets | Python 3 (NumPy, SciPy, Pillow) scripts in `tools/maya/` generate Maya's animation frames |
| Hosting | **Vercel**, using its container runtime with the root `Dockerfile` |

### Object-oriented design

The Java model is built around three class hierarchies:

| What varies | Design | Classes |
| --- | --- | --- |
| Type of business | Abstract class with subclasses | `Industry` → `RetailIndustry`, `FoodIndustry`, `ManufacturingIndustry`, `ProfessionalServicesIndustry`, `HealthcareIndustry` |
| Business size | Enum with constant-specific methods | `BusinessSize`: `MICRO`, `SMALL`, `MEDIUM`, `LARGE` |
| Service | Abstract class with a final template method `quote()` | `PermitService` → `NewPermitService`, `RenewalService`, `InspectionVisitService` |

Storage goes through the `AppointmentRepository` interface, implemented by `SupabaseAppointmentRepository` (the live database) and `InMemoryAppointmentRepository` (local testing); holidays come from the `HolidayCalendar` interface.

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
supabase/          database migration (the appointments table)
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

Without database settings, bookings are kept in memory and reset when the server restarts. To store them in Supabase:

1. Run `supabase/migrations/20261114000000_create_appointments.sql` in your Supabase project (SQL Editor), or let the Supabase GitHub integration apply it.
2. Set `SUPABASE_URL` (Project Settings → API) and `SUPABASE_SERVICE_ROLE_KEY` (or the newer secret key as `SUPABASE_SECRET_KEY`) as environment variables. Never put this key in the front end.

### Verify before booking (anti-bot)

With eGov settings, `/book.html` opens on a **Verify** screen before the booking steps. The person chooses one of:

- **Verify with eGovPH:** the eGov SSO "Login as eGov" widget (mobile OTP and eGov PIN). Name, email and mobile are filled in. Needs `EGOV_BASE_URL`, `EGOV_PARTNER_CODE`, `EGOV_PARTNER_SECRET` ([eGov SSO](https://platforms.e.gov.ph/api-catalogs/egov-sso)).
- **Scan your ID:** a photo of a government ID. The server reads its text with [Tesseract](https://github.com/tesseract-ocr/tesseract) OCR, keeping each line's position on the card, then an LLM (default `claude-sonnet-5-5`) through [AssemblyAI's LLM Gateway](https://www.assemblyai.com/docs/llm-gateway/quickstart) sorts the lines into name, birth date, ID number and expiry. The gateway only takes text, so it never sees the photo; the photo is deleted right after OCR. The name is filled in; expired IDs and non-IDs are rejected. Needs `ASSEMBLYAI_API_KEY` and Tesseract (`brew install tesseract` locally; the Dockerfile installs it). Optional: `ID_SCAN_MODEL`, `TESSERACT_PATH`.

Either way the server returns a signed 30-minute pass that booking requires; the booker's name comes from it and can't be edited. Each verified person can hold at most 3 active bookings. Run `supabase/migrations/20261115000000_add_verified_id.sql` first. Without any eGov settings anyone can book, as before. Secrets stay on the server.

---

## API

| Method and path | What it does |
| --- | --- |
| `GET /api/services`, `/api/industries`, `/api/offices`, `/api/config` | Lists services, types of business, city offices and size tiers |
| `GET /api/quote?serviceId=&industryId=&employees=` | Fee estimate, inspections, documents and time |
| `GET /api/slots?officeId=&date=YYYY-MM-DD` | Available time slots for an office and day |
| `POST /api/verify/egov` | Redeems the eGovPH widget's exchange code; returns a signed 30-minute pass and the person's details |
| `POST /api/verify/id` | Reads an ID photo (raw JPEG/PNG/WebP body, max 8 MB); returns a pass and the name on the ID |
| `POST /api/appointments` | Books a slot and returns the reference number (needs `verifyPass` when verification is on) |
| `GET /api/appointments/{ref}` | Looks up a booking |
| `POST /api/appointments/{ref}/cancel` | Cancels a booking |
| `GET /api/availability?officeId=&month=YYYY-MM` | Every day of a month for an office: open or why closed (weekend, holiday, outside the window), places left. Drives the month calendar |
| `POST /api/appointments/{ref}/pay` | Starts a **demo** fee payment (`channel`); the amount is computed by the server. No money moves |
| `GET /api/pay/{txn}` · `POST /api/pay/{txn}/complete` | Demo checkout opened from the payment QR (`/pay.html?txn=…`); completing it issues a demo acknowledgement receipt |
| `POST /api/appointments/{ref}/reschedule` | Moves a booking to a new date and time |
| `GET /api/office/{ref}` · `POST /api/office/{ref}/accept` | Staff-only live check and office acceptance; requires `X-Office-Key` matching `OFFICE_ACCESS_KEY` |

### Printing and office check-in

The booking confirmation and demo acknowledgement receipt have separate print layouts containing only the appointment or payment details, the reference number, and an office check-in QR. The code opens `/office.html?ref=…`; it does not itself accept a booking or assert that payment was made. Staff enter the office access key, see the live booking and demo payment status (`paid`, `pending`, or `no payment recorded`), then explicitly accept the booking. Acceptance is recorded once and prevents online cancellation or rescheduling. It does not change payment status.

Set a strong `OFFICE_ACCESS_KEY` in the server environment to enable office check-in. Without it, the staff API reports that check-in is unavailable. Apply `supabase/migrations/20261117000000_add_office_acceptance.sql` before accepting bookings when using Supabase. The key is entered on the office page and sent only in the `X-Office-Key` request header; do not put it in the QR or a URL. The existing payment flow remains a demo: no money moves, and the printed acknowledgement is not an official receipt.
