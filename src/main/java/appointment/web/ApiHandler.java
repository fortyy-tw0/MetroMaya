package appointment.web;

import appointment.model.Applicant;
import appointment.model.Appointment;
import appointment.model.Business;
import appointment.model.BusinessSize;
import appointment.model.FeeLine;
import appointment.model.Industry;
import appointment.model.InspectionType;
import appointment.model.Office;
import appointment.model.Payment;
import appointment.model.PermitService;
import appointment.model.Quote;
import appointment.service.AppointmentService;
import appointment.service.BookingException;
import appointment.service.EgovVerifier;
import appointment.service.IdScanner;
import appointment.service.VerificationPass;
import appointment.service.VerifiedPerson;
import appointment.service.NotFoundException;
import appointment.service.SlotInfo;
import appointment.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
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
 */
class ApiHandler implements HttpHandler {

    private static final int MAX_BODY_BYTES = 16 * 1024;

    private final AppointmentService appointments;
    /** Each null when its credentials aren't set. When either is on, booking needs a pass from {@link #passes}. */
    private final EgovVerifier egov;
    private final IdScanner idScanner;
    private final VerificationPass passes;

    ApiHandler(AppointmentService appointments, EgovVerifier egov, IdScanner idScanner, VerificationPass passes) {
        this.appointments = appointments;
        this.egov = egov;
        this.idScanner = idScanner;
        this.passes = passes;
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
                respond(exchange, 201, appointmentJson(book(readForm(exchange))));
                return;
            }
            if (method.equals("GET") && parts.length == 2) {
                respond(exchange, 200, appointmentJson(appointments.find(parts[1])));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("cancel")) {
                respond(exchange, 200, appointmentJson(appointments.cancel(parts[1])));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("pay")) {
                Appointment started = appointments.startPayment(parts[1], readForm(exchange).get("channel"));
                respond(exchange, 200, appointmentJson(started));
                return;
            }
            if (method.equals("POST") && parts.length == 3 && parts[2].equals("reschedule")) {
                Map<String, String> form = readForm(exchange);
                Appointment moved = appointments.reschedule(parts[1],
                        LocalDate.parse(form.getOrDefault("date", "")),
                        LocalTime.parse(form.getOrDefault("time", "")));
                respond(exchange, 200, appointmentJson(moved));
                return;
            }
        }
        respond(exchange, 404, Map.of("error", "Unknown API endpoint."));
    }

    private Appointment book(Map<String, String> form) {
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
        return Map.of("appointment", m);
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
