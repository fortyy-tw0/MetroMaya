package appointment.web;

import appointment.model.Applicant;
import appointment.model.Appointment;
import appointment.model.Business;
import appointment.model.BusinessSize;
import appointment.model.FeeLine;
import appointment.model.Industry;
import appointment.model.InspectionType;
import appointment.model.Office;
import appointment.model.OfficePolicy;
import appointment.model.Payment;
import appointment.model.PermitService;
import appointment.model.Quote;
import appointment.model.StaffAccount;
import appointment.service.AppointmentService;
import appointment.service.BookingException;
import appointment.service.EgovVerifier;
import appointment.service.IdScanner;
import appointment.service.VerificationPass;
import appointment.service.VerifiedPerson;
import appointment.service.NotFoundException;
import appointment.service.SlotInfo;
import appointment.service.StaffAuthService;
import appointment.service.NotificationService;
import appointment.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON API:
 *   GET  /api/health
 *   GET  /api/config                        today's date and the booking window
 *   GET  /api/services                      new permit, renewal, inspection visit
 *   GET  /api/industries                    types of business and what each one requires
 *   GET  /api/offices
 *   GET  /api/quote?serviceId=&industryId=&employees=
 *   GET  /api/slots?officeId=&date=YYYY-MM-DD
 *   GET  /api/availability?officeId=&month=YYYY-MM   every day of the month: open or why closed, places left
 *   POST /api/verify/egov                   (exchangeCode from the eGovPH widget) → pass + name, email, mobile
 *   POST /api/verify/id                     (raw ID photo or PDF as the body) → pass + name read from the ID
 *   POST /api/appointments                  (form-encoded body; needs verifyPass when verification is on)
 *   GET  /api/appointments/{ref}
 *   POST /api/appointments/{ref}/cancel
 *   POST /api/appointments/{ref}/reschedule (date, time)
 *   POST /api/appointments/{ref}/pay        (channel) → starts a DEMO fee payment (no money moves)
 *   GET  /api/pay/{txn}                     demo checkout details (opened by scanning the payment QR)
 *   POST /api/pay/{txn}/complete            marks the demo payment paid and issues a demo receipt
 *   /api/staff/*                             role- and office-scoped staff operations
 */
class ApiHandler implements HttpHandler {

    private static final int MAX_BODY_BYTES = 16 * 1024;

    private final AppointmentService appointments;
    /** Each null when its credentials aren't set. When either is on, booking needs a pass from {@link #passes}. */
    private final EgovVerifier egov;
    private final IdScanner idScanner;
    private final VerificationPass passes;
    private final StaffAuthService staff;
    private final NotificationService notifications;

    ApiHandler(AppointmentService appointments, EgovVerifier egov, IdScanner idScanner, VerificationPass passes,
               StaffAuthService staff, NotificationService notifications) {
        this.appointments = appointments;
        this.egov = egov;
        this.idScanner = idScanner;
        this.passes = passes;
        this.staff = staff;
        this.notifications = notifications;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                route(exchange);
            } catch (BookingException | IllegalArgumentException e) {
                respond(exchange, 400, Map.of("error", e.getMessage()));
            } catch (NotFoundException e) {
                respond(exchange, 404, Map.of("error", e.getMessage()));
            } catch (StaffAuthService.AccessDenied e) {
                respond(exchange, e.status(), Map.of("error", e.getMessage()));
            } catch (DateTimeParseException e) {
                respond(exchange, 400, Map.of("error", "Invalid date or time format."));
            } catch (IllegalStateException e) {
                // the bookings database could not be reached; the message is safe to show
                respond(exchange, 503, Map.of("error", e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                respond(exchange, 500, Map.of("error", "Something went wrong on our side. Please try again."));
            }
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.substring("/api/".length()).split("/");
        String head = parts[0];

        if (head.equals("staff")) {
            staffRoute(exchange, method, parts);
            return;
        }

        if (method.equals("GET") && parts.length == 1) {
            switch (head) {
                case "health" -> { respond(exchange, 200, Map.of("status", "ok")); return; }
                case "config" -> { respond(exchange, 200, configJson()); return; }
                case "services" -> { respond(exchange, 200, servicesJson()); return; }
                case "industries" -> { respond(exchange, 200, industriesJson()); return; }
                case "offices" -> { respond(exchange, 200, officesJson()); return; }
                case "quote" -> { respond(exchange, 200, quoteResponse(exchange)); return; }
                case "slots" -> { respond(exchange, 200, slotsJson(exchange)); return; }
                case "availability" -> { respond(exchange, 200, availabilityJson(exchange)); return; }
                case "checklist" -> { respond(exchange, 200, checklistJson(exchange)); return; }
                default -> { }
            }
        }

        if (head.equals("verify") && method.equals("POST") && parts.length == 2) {
            VerifiedPerson person = switch (parts[1]) {
                case "egov" -> egov == null ? null : egov.redeem(readForm(exchange).get("exchangeCode"));
                case "id" -> idScanner == null ? null : idScanner.scan(readUpload(exchange));
                default -> null;
            };
            if (person == null) {
                respond(exchange, 404, Map.of("error", "That verification method is not turned on."));
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("pass", passes.issue(person));
            out.put("fullName", person.fullName());
            out.put("email", person.email());
            out.put("mobile", person.mobile());
            out.put("method", person.method());
            if (person.address() != null) {
                // only to prefill the form; the address is not part of the pass and is not stored here
                VerifiedPerson.Address a = person.address();
                Map<String, Object> addr = new LinkedHashMap<>();
                addr.put("line1", a.line1());
                addr.put("barangay", a.barangay());
                addr.put("city", a.city());
                addr.put("province", a.province());
                addr.put("zip", a.zip());
                out.put("address", addr);
            }
            respond(exchange, 200, out);
            return;
        }

        if (head.equals("pay") && parts.length >= 2) {
            if (method.equals("GET") && parts.length == 2) {
                respond(exchange, 200, checkoutJson(appointments.findByPaymentTxn(parts[1])));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("complete")) {
                respond(exchange, 200, checkoutJson(appointments.completePayment(parts[1])));
                return;
            }
        }

        if (head.equals("appointments")) {
            if (method.equals("POST") && parts.length == 1) {
                AppointmentService.BookingResult created = book(readForm(exchange));
                Map<String, Object> body = bookingResultJson(created);
                body.put("emailSent", notifications.confirmation(created));
                respond(exchange, 201, body);
                return;
            }
            if (method.equals("GET") && parts.length == 2) {
                respond(exchange, 200, appointmentJson(appointments.findManaged(parts[1], managementToken(exchange))));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("cancel")) {
                respond(exchange, 200, appointmentJson(appointments.cancel(parts[1], managementToken(exchange))));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("pay")) {
                Appointment started = appointments.startPayment(parts[1], readForm(exchange).get("channel"), managementToken(exchange));
                respond(exchange, 200, appointmentJson(started));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("reschedule")) {
                Map<String, String> form = readForm(exchange);
                Appointment moved = appointments.reschedule(parts[1],
                        LocalDate.parse(form.getOrDefault("date", "")),
                        LocalTime.parse(form.getOrDefault("time", "")), managementToken(exchange));
                respond(exchange, 200, appointmentJson(moved));
                return;
            }
        }
        respond(exchange, 404, Map.of("error", "Unknown API endpoint."));
    }

    private void staffRoute(HttpExchange exchange, String method, String[] parts) throws IOException {
        if (method.equals("POST") && parts.length == 2 && parts[1].equals("login")) {
            Map<String, String> form = readForm(exchange);
            StaffAuthService.Login login = staff.login(form.get("username"), form.get("password"));
            respond(exchange, 200, Map.of("token", login.token(), "user", StaffAuthService.publicAccount(login.account())));
            return;
        }
        StaffAccount user = staff.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
        if (method.equals("GET") && parts.length == 2 && parts[1].equals("me")) {
            respond(exchange, 200, Map.of("user", StaffAuthService.publicAccount(user)));
            return;
        }
        if (parts.length >= 2 && parts[1].equals("policy")) {
            if (method.equals("GET") && parts.length == 2) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                String officeId = staff.officeFor(user, query.get("officeId"));
                respond(exchange, 200, Map.of("policy", policyJson(appointments.policy(officeId))));
                return;
            }
            staff.requireRole(user, StaffAccount.Role.DISTRICT_HEAD);
            Map<String, String> form = readForm(exchange);
            String officeId = staff.officeFor(user, form.get("officeId"));
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("schedule")) {
                List<LocalTime> times = java.util.Arrays.stream(form.getOrDefault("times", "").split(","))
                        .map(String::trim).filter(s -> !s.isBlank()).map(LocalTime::parse).toList();
                int capacity = Integer.parseInt(form.getOrDefault("capacity", "0"));
                respond(exchange, 200, Map.of("policy", policyJson(appointments.updateSchedule(officeId, times, capacity))));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("closure")) {
                LocalDate date = LocalDate.parse(form.getOrDefault("date", ""));
                OfficePolicy.Closure closure = "remove".equals(form.get("action")) ? null
                        : new OfficePolicy.Closure(form.getOrDefault("kind", ""), form.get("reason"));
                OfficePolicy updated = appointments.updateClosure(officeId, date, closure);
                List<Appointment> affectedBookings = appointments.listForOffice(officeId, date, date).stream()
                        .filter(a -> a.isActive() && !a.isWalkIn() && a.getAcceptedAt() == null).toList();
                List<Map<String, Object>> affected = affectedBookings.stream()
                        .map(a -> Map.<String, Object>of("referenceNo", a.getReferenceNo(),
                                "email", a.getApplicant().getEmail(), "time", a.getTime().toString())).toList();
                int notified = 0;
                if (closure != null) for (Appointment a : affectedBookings)
                    if (notifications.closure(a, closure.reason())) notified++;
                respond(exchange, 200, Map.of("policy", policyJson(updated), "affectedBookings", affected,
                        "emailsSent", notified));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("checklist")) {
                List<String> documents = java.util.Arrays.stream(form.getOrDefault("documents", "").split("\\r?\\n"))
                        .map(String::trim).filter(s -> !s.isBlank()).toList();
                OfficePolicy updated = appointments.updateChecklist(officeId, form.get("serviceId"),
                        documents, form.getOrDefault("instructions", ""));
                respond(exchange, 200, Map.of("policy", policyJson(updated)));
                return;
            }
        }
        if (method.equals("GET") && parts.length == 2 && parts[1].equals("bookings")) {
            staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD, StaffAccount.Role.TREASURER);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String officeId = staff.officeFor(user, query.get("officeId"));
            LocalDate from = LocalDate.parse(query.getOrDefault("from", appointments.today().toString()));
            LocalDate to = LocalDate.parse(query.getOrDefault("to", from.toString()));
            List<Map<String, Object>> rows = appointments.listForOffice(officeId, from, to).stream()
                    .map(this::appointmentData).toList();
            respond(exchange, 200, Map.of("officeId", officeId, "bookings", rows));
            return;
        }
        if (method.equals("POST") && parts.length == 2 && parts[1].equals("walkins")) {
            staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD);
            Map<String, String> form = readForm(exchange);
            String officeId = staff.officeFor(user, form.get("officeId"));
            Business business = new Business(form.get("businessName"), form.get("businessAddress"),
                    appointments.getCatalog().getIndustry(form.get("industryId")), parseEmployees(form.get("employees")));
            Applicant applicant = new Applicant(form.get("ownerName"), form.get("email"), form.get("phone"));
            Appointment walkIn = appointments.createWalkIn(form.get("serviceId"), officeId, applicant, business);
            respond(exchange, 201, appointmentJson(walkIn));
            return;
        }
        if (method.equals("GET") && parts.length == 2 && parts[1].equals("reconciliation")) {
            staff.requireRole(user, StaffAccount.Role.TREASURER, StaffAccount.Role.DISTRICT_HEAD);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String officeId = staff.officeFor(user, query.get("officeId"));
            LocalDate from = LocalDate.parse(query.getOrDefault("from", appointments.today().toString()));
            LocalDate to = LocalDate.parse(query.getOrDefault("to", from.toString()));
            List<Map<String, Object>> rows = appointments.listForOffice(officeId, from, to).stream()
                    .map(this::appointmentData).toList();
            respond(exchange, 200, Map.of("officeId", officeId, "bookings", rows));
            return;
        }
        if (method.equals("GET") && parts.length == 3 && parts[1].equals("checkin")) {
            staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD);
            Appointment booking = appointments.findByCheckInCode(parts[2]);
            staff.requireOffice(user, booking.getOffice().getId());
            respond(exchange, 200, appointmentJson(booking));
            return;
        }
        if (parts.length >= 3 && parts[1].equals("bookings")) {
            staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD, StaffAccount.Role.TREASURER);
            Appointment booking = appointments.find(parts[2]);
            staff.requireOffice(user, booking.getOffice().getId());
            if (method.equals("GET") && parts.length == 3) {
                respond(exchange, 200, appointmentJson(booking));
                return;
            }
            if (method.equals("POST") && parts.length == 4 && parts[3].equals("accept")) {
                staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD);
                respond(exchange, 200, appointmentJson(appointments.acceptAtOffice(parts[2])));
                return;
            }
            if (method.equals("POST") && parts.length == 4 && parts[3].equals("no-show")) {
                staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD);
                respond(exchange, 200, appointmentJson(appointments.markNoShow(parts[2])));
                return;
            }
            if (method.equals("POST") && parts.length == 4 && parts[3].equals("counter-payment")) {
                staff.requireRole(user, StaffAccount.Role.TREASURER);
                Map<String, String> form = readForm(exchange);
                double amount = Double.parseDouble(form.getOrDefault("amount", ""));
                respond(exchange, 200, appointmentJson(appointments.recordCounterPayment(parts[2], amount,
                        form.get("officialReceiptNo"), user.username())));
                return;
            }
            if (method.equals("POST") && parts.length == 4 && parts[3].equals("move")) {
                staff.requireRole(user, StaffAccount.Role.DISTRICT_HEAD);
                Map<String, String> form = readForm(exchange);
                Appointment moved = appointments.moveAtOffice(parts[2],
                        LocalDate.parse(form.getOrDefault("date", "")),
                        LocalTime.parse(form.getOrDefault("time", "")));
                Map<String, Object> result = new LinkedHashMap<>(appointmentJson(moved));
                result.put("emailSent", notifications.moved(moved));
                respond(exchange, 200, result);
                return;
            }
            if (method.equals("POST") && parts.length == 4 && parts[3].equals("reset-management")) {
                staff.requireRole(user, StaffAccount.Role.STAFF, StaffAccount.Role.DISTRICT_HEAD);
                respond(exchange, 200, bookingResultJson(appointments.resetManagement(parts[2])));
                return;
            }
        }
        if (method.equals("GET") && parts.length == 2 && parts[1].equals("analytics")) {
            staff.requireRole(user, StaffAccount.Role.DISTRICT_HEAD);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String officeId = staff.officeFor(user, query.get("officeId"));
            YearMonth month = YearMonth.parse(query.getOrDefault("month", YearMonth.from(appointments.today()).toString()));
            List<Appointment> bookings = appointments.listForOffice(officeId, month.atDay(1), month.atEndOfMonth());
            List<Map<String, Object>> days = new ArrayList<>();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                List<Appointment> daily = bookings.stream().filter(a -> a.getDate().equals(date)).toList();
                days.add(Map.of("date", date.toString(), "booked", daily.size(),
                        "active", daily.stream().filter(Appointment::isActive).count(),
                        "checkedIn", daily.stream().filter(a -> a.getAcceptedAt() != null).count(),
                        "noShows", daily.stream().filter(a -> a.getStatus().name().equals("NO_SHOW")).count(),
                        "walkIns", daily.stream().filter(Appointment::isWalkIn).count(),
                        "paidOnline", daily.stream().filter(a -> a.getPayment() != null && a.getPayment().isPaid()).count()));
            }
            respond(exchange, 200, Map.of("officeId", officeId, "month", month.toString(), "days", days,
                    "totalBookings", bookings.size(),
                    "totalCheckIns", bookings.stream().filter(a -> a.getAcceptedAt() != null).count()));
            return;
        }
        if (parts.length == 2 && parts[1].equals("users")) {
            staff.requireRole(user, StaffAccount.Role.ADMIN);
            if (method.equals("GET")) {
                respond(exchange, 200, Map.of("users", staff.users()));
                return;
            }
            if (method.equals("POST")) {
                Map<String, String> form = readForm(exchange);
                StaffAccount created = staff.create(form.get("username"), form.get("password"),
                        StaffAccount.Role.valueOf(form.getOrDefault("role", "")), form.get("officeId"));
                respond(exchange, 201, Map.of("user", StaffAuthService.publicAccount(created)));
                return;
            }
        }
        if (parts.length == 4 && parts[1].equals("users") && method.equals("POST")) {
            staff.requireRole(user, StaffAccount.Role.ADMIN);
            StaffAccount updated = switch (parts[3]) {
                case "disable" -> staff.setActive(parts[2], false);
                case "enable" -> staff.setActive(parts[2], true);
                case "reset-password" -> staff.resetPassword(parts[2], readForm(exchange).get("password"));
                default -> null;
            };
            if (updated != null) {
                respond(exchange, 200, Map.of("user", StaffAuthService.publicAccount(updated)));
                return;
            }
        }
        if (method.equals("GET") && parts.length == 2 && parts[1].equals("system")) {
            staff.requireRole(user, StaffAccount.Role.ADMIN);
            respond(exchange, 200, Map.of("staffLogin", staff.configured(),
                    "emailNotifications", notifications.configured(),
                    "demoSeedEnabled", "true".equalsIgnoreCase(System.getenv("ALLOW_DEMO_SEED")),
                    "egovVerification", egov != null, "idScan", idScanner != null,
                    "persistentStorage", System.getenv("SUPABASE_URL") != null));
            return;
        }
        if (method.equals("POST") && parts.length == 2 && parts[1].equals("seed-heads")) {
            staff.requireRole(user, StaffAccount.Role.ADMIN);
            respond(exchange, 200, Map.of("issued", staff.seedDistrictHeads()));
            return;
        }
        if (method.equals("POST") && parts.length == 2 && parts[1].equals("simulate-full-day")) {
            staff.requireRole(user, StaffAccount.Role.ADMIN);
            if (!"true".equalsIgnoreCase(System.getenv("ALLOW_DEMO_SEED")))
                throw new StaffAuthService.AccessDenied(403, "Demo data seeding is off. Set ALLOW_DEMO_SEED=true on a test server.");
            Map<String, String> form = readForm(exchange);
            String officeId = staff.officeFor(user, form.get("officeId"));
            LocalDate date = LocalDate.parse(form.getOrDefault("date", ""));
            respond(exchange, 200, Map.of("officeId", officeId, "date", date.toString(),
                    "created", appointments.seedFullyBookedDay(officeId, date)));
            return;
        }
        if (method.equals("POST") && parts.length == 2 && parts[1].equals("send-reminders")) {
            staff.requireRole(user, StaffAccount.Role.ADMIN);
            respond(exchange, 200, Map.of("emailsSent", notifications.remindTomorrow(),
                    "configured", notifications.configured()));
            return;
        }
        respond(exchange, 404, Map.of("error", "Unknown staff endpoint."));
    }

    private AppointmentService.BookingResult book(Map<String, String> form) {
        Industry industry = appointments.getCatalog().getIndustry(form.get("industryId"));
        Business business = new Business(
                form.get("businessName"),
                form.get("businessAddress"),
                industry,
                parseEmployees(form.get("employees")));
        Applicant applicant;
        if (passes != null) {
            // the booker's name and ID come from the signed pass, never from the form
            VerifiedPerson p = passes.check(form.get("verifyPass"));
            applicant = new Applicant(p.fullName(), form.get("email"), form.get("phone"), p.id());
        } else {
            applicant = new Applicant(form.get("ownerName"), form.get("email"), form.get("phone"));
        }
        return appointments.book(
                form.get("serviceId"),
                form.get("officeId"),
                LocalDate.parse(form.getOrDefault("date", "")),
                LocalTime.parse(form.getOrDefault("time", "")),
                applicant, business);
    }

    private static int parseEmployees(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Number of employees must be a whole number.");
        }
    }

    // ---- JSON mapping ----

    private Map<String, Object> configJson() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("today", appointments.today().toString());
        cfg.putAll(windowJson());
        List<Map<String, Object>> sizes = new ArrayList<>();
        for (BusinessSize size : BusinessSize.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", size.name());
            m.put("label", size.getLabel());
            m.put("range", size.getRange());
            m.put("feeMultiplier", size.getFeeMultiplier());
            m.put("inspectionFactor", size.getInspectionFactor());
            m.put("extraInspections", size.extraInspections().stream().map(InspectionType::getLabel).toList());
            m.put("extraDocuments", size.extraDocuments());
            sizes.add(m);
        }
        cfg.put("sizes", sizes);
        if (passes != null) {
            // verification comes first on the booking page; the widget needs partnerCode and host (secrets stay here)
            Map<String, Object> verify = new LinkedHashMap<>();
            verify.put("egov", egov == null ? null : Map.of("partnerCode", egov.getPartnerCode(), "host", egov.getBaseUrl()));
            verify.put("idScan", idScanner != null);
            cfg.put("verify", verify);
        }
        return cfg;
    }

    /** The booking window: up to 3 months ahead, minus weekends and the regular holidays listed here. */
    private Map<String, Object> windowJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate today = appointments.today(), last = appointments.lastBookableDate();
        m.put("maxDate", last.toString());
        m.put("maxDaysAhead", java.time.temporal.ChronoUnit.DAYS.between(today, last));
        m.put("maxMonthsAhead", AppointmentService.MAX_MONTHS_AHEAD);
        List<Map<String, Object>> holidays = new ArrayList<>();
        appointments.upcomingHolidays().forEach(h -> holidays.add(Map.of("date", h.date().toString(), "name", h.name())));
        m.put("holidays", holidays);
        return m;
    }

    private Map<String, Object> servicesJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PermitService s : appointments.getCatalog().getServices()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            m.put("baseDocuments", s.baseDocuments());
            list.add(m);
        }
        return Map.of("services", list);
    }

    private Map<String, Object> industriesJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Industry i : appointments.getCatalog().getIndustries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("name", i.getName());
            m.put("description", i.getDescription());
            m.put("baseFee", i.getBaseFee());
            m.put("inspections", i.requiredInspections().stream().map(InspectionType::getLabel).toList());
            m.put("documents", i.requiredDocuments());
            m.put("surcharges", feeLines(i.surcharges()));
            list.add(m);
        }
        return Map.of("industries", list);
    }

    private Map<String, Object> officesJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Office o : appointments.getCatalog().getOffices()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("name", o.getName());
            m.put("address", o.getAddress());
            m.put("serviceIds", o.getServiceIds().stream().sorted().toList());
            m.put("sizesServed", java.util.Arrays.stream(BusinessSize.values())
                    .filter(o::serves).map(Enum::name).toList());
            list.add(m);
        }
        return Map.of("offices", list);
    }

    private Map<String, Object> checklistJson(HttpExchange exchange) {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String officeId = query.get("officeId"), serviceId = query.get("serviceId");
        appointments.getCatalog().getOffice(officeId);
        appointments.getCatalog().getService(serviceId);
        OfficePolicy.Checklist checklist = appointments.policy(officeId).checklists().get(serviceId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("officeId", officeId);
        data.put("serviceId", serviceId);
        data.put("documents", checklist == null ? List.of() : checklist.documents());
        data.put("instructions", checklist == null ? "" : checklist.instructions());
        data.put("updatedAt", checklist == null ? null : checklist.updatedAt().toString());
        return Map.of("checklist", data);
    }

    private static Map<String, Object> policyJson(OfficePolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("officeId", policy.officeId());
        data.put("times", policy.slotTimes().stream().map(LocalTime::toString).toList());
        data.put("capacity", policy.capacityPerSlot());
        List<Map<String, Object>> closures = new ArrayList<>();
        policy.closures().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                closures.add(Map.of("date", entry.getKey().toString(), "kind", entry.getValue().kind(),
                        "reason", entry.getValue().reason())));
        data.put("closures", closures);
        Map<String, Object> checklists = new LinkedHashMap<>();
        policy.checklists().forEach((service, checklist) -> checklists.put(service,
                Map.of("documents", checklist.documents(), "instructions", checklist.instructions(),
                        "updatedAt", checklist.updatedAt().toString())));
        data.put("checklists", checklists);
        return data;
    }

    private Map<String, Object> quoteResponse(HttpExchange exchange) {
        Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
        Quote quote = appointments.quote(q.get("serviceId"), q.get("industryId"), parseEmployees(q.get("employees")));
        return Map.of("quote", quoteJson(quote));
    }

    private Map<String, Object> quoteJson(Quote quote) {
        BusinessSize size = quote.size();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> sizeJson = new LinkedHashMap<>();
        sizeJson.put("id", size.name());
        sizeJson.put("label", size.getLabel());
        sizeJson.put("range", size.getRange());
        m.put("size", sizeJson);

        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("lines", feeLines(quote.fee().getLines()));
        fee.put("total", quote.fee().getTotal());
        m.put("fee", fee);

        List<Map<String, Object>> inspections = new ArrayList<>();
        for (InspectionType type : quote.inspections()) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("label", type.getLabel());
            i.put("fee", Math.round(type.getFee() * size.getInspectionFactor() * 100.0) / 100.0);
            i.put("minutes", (int) Math.round(type.getMinutes() * size.getInspectionFactor()));
            inspections.add(i);
        }
        m.put("inspections", inspections);
        m.put("requirements", quote.requirements());
        m.put("estimatedMinutes", quote.estimatedMinutes());
        return m;
    }

    private List<Map<String, Object>> feeLines(List<FeeLine> lines) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FeeLine line : lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", line.label());
            m.put("amount", line.amount());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> slotsJson(HttpExchange exchange) {
        Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
        List<SlotInfo> slots = appointments.getSlots(q.get("officeId"), LocalDate.parse(q.getOrDefault("date", "")));
        List<Map<String, Object>> list = new ArrayList<>();
        for (SlotInfo s : slots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", s.time().toString());
            m.put("remaining", s.remaining());
            m.put("capacity", s.capacity());
            m.put("available", s.available());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", appointments.today().toString());
        out.putAll(windowJson());
        out.put("slots", list);
        return out;
    }

    private Map<String, Object> appointmentJson(Appointment a) {
        return Map.of("appointment", appointmentData(a));
    }

    private Map<String, Object> appointmentData(Appointment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("referenceNo", a.getReferenceNo());
        m.put("status", a.getStatus().name());
        m.put("date", a.getDate().toString());
        m.put("time", a.getTime().toString());
        m.put("serviceId", a.getService().getId());
        m.put("serviceName", a.getService().getName());
        m.put("officeId", a.getOffice().getId());
        m.put("officeName", a.getOffice().getName());
        m.put("officeAddress", a.getOffice().getAddress());
        m.put("businessName", a.getBusiness().getName());
        m.put("businessAddress", a.getBusiness().getAddress());
        m.put("industryName", a.getBusiness().getIndustry().getName());
        m.put("employees", a.getBusiness().getEmployeeCount());
        m.put("ownerName", a.getApplicant().getFullName());
        m.put("email", a.getApplicant().getEmail());
        m.put("verified", a.getApplicant().isVerified());
        m.put("quote", quoteJson(a.getQuote()));
        m.put("amountDue", appointments.amountDue(a));
        m.put("payment", paymentJson(a.getPayment()));
        m.put("counterPayment", a.getCounterPayment() == null ? null : a.getCounterPayment().toMap());
        m.put("acceptedAt", a.getAcceptedAt() == null ? null : a.getAcceptedAt().toString());
        m.put("queueNumber", a.getQueueNumber());
        m.put("walkIn", a.isWalkIn());
        m.put("demoRecord", a.isDemoRecord());
        m.put("noShowAt", a.getNoShowAt() == null ? null : a.getNoShowAt().toString());
        m.put("checkInCode", a.getCheckInCode());
        return m;
    }

    private Map<String, Object> bookingResultJson(AppointmentService.BookingResult result) {
        Map<String, Object> body = new LinkedHashMap<>(appointmentJson(result.appointment()));
        body.put("managementToken", result.managementToken());
        return body;
    }

    private static String managementToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-Management-Token");
        return header == null ? null : header.trim();
    }

    private Map<String, Object> availabilityJson(HttpExchange exchange) {
        Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
        java.time.YearMonth month;
        try {
            month = java.time.YearMonth.parse(q.getOrDefault("month", ""));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Month must look like 2026-11.");
        }
        List<Map<String, Object>> days = new ArrayList<>();
        for (AppointmentService.DayAvailability d : appointments.monthAvailability(q.get("officeId"), month)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.date().toString());
            m.put("open", d.open());
            m.put("reason", d.closedReason());
            m.put("remaining", d.remaining());
            m.put("capacity", d.capacity());
            days.add(m);
        }
        return Map.of("month", month.toString(), "days", days);
    }

    private static Map<String, Object> paymentJson(Payment p) {
        if (p == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txn", p.getTransactionId());
        m.put("channel", p.getChannel());
        m.put("amount", p.getAmount());
        m.put("status", p.getStatus().name());
        m.put("receiptNo", p.getReceiptNo());
        m.put("paidAt", p.getPaidAt() == null ? null : p.getPaidAt().toString());
        m.put("demo", true);
        return m;
    }

    /** What the demo checkout page shows: just enough to pay, no personal details beyond the business name. */
    private Map<String, Object> checkoutJson(Appointment a) {
        Map<String, Object> m = new LinkedHashMap<>(paymentJson(a.getPayment()));
        m.put("referenceNo", a.getReferenceNo());
        m.put("description", a.getService().getName() + " · " + a.getBusiness().getName());
        m.put("payTo", a.getOffice().getName());
        m.put("bookingActive", a.isActive());
        return Map.of("payment", m);
    }

    // ---- HTTP helpers ----

    private byte[] readUpload(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(IdScanner.MAX_BYTES + 1);
        if (body.length > IdScanner.MAX_BYTES) {
            throw new IllegalArgumentException("That file is too large. Use a photo under 8 MB.");
        }
        return body;
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Request is too large.");
        }
        return parseQuery(new String(body, StandardCharsets.UTF_8));
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
