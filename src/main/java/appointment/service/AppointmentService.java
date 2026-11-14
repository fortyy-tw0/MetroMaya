package appointment.service;

import appointment.model.Applicant;
import appointment.model.Appointment;
import appointment.model.Business;
import appointment.model.CounterPayment;
import appointment.model.FeeLine;
import appointment.model.Office;
import appointment.model.OfficePolicy;
import appointment.model.Payment;
import appointment.model.PermitService;
import appointment.model.Quote;
import appointment.repository.AppointmentRepository;
import appointment.repository.OfficePolicyRepository;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All booking rules live here:
 * weekdays that aren't regular holidays, up to 3 months ahead, fixed hourly slots, per-slot capacity,
 * offices that only offer some services and serve some business sizes,
 * one active booking per business per service, and at most 3 active bookings per verified person (eGovPH or ID scan).
 */
public class AppointmentService {

    /** Bookings open up to this many months ahead. */
    public static final int MAX_MONTHS_AHEAD = 3;

    /** Hourly slots, 8:00 to 16:00, with a lunch break at 12:00. */
    public static final List<LocalTime> SLOT_TIMES = List.of(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
            LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));

    /** A verified person may hold at most this many active bookings at once (anti-spam). */
    public static final int MAX_ACTIVE_PER_PERSON = 3;

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppointmentRepository repository;
    private final CatalogService catalog;
    private final Clock clock;
    private final HolidayCalendar holidays;
    private final OfficePolicyRepository policies;
    private final ManagementLinkVault linkVault;
    private final SecureRandom random = new SecureRandom();

    /** The raw management token is returned once; only its SHA-256 hash is stored. */
    public record BookingResult(Appointment appointment, String managementToken) {}

    public AppointmentService(AppointmentRepository repository, CatalogService catalog, Clock clock,
                              HolidayCalendar holidays, OfficePolicyRepository policies, ManagementLinkVault linkVault) {
        this.repository = repository;
        this.catalog = catalog;
        this.clock = clock;
        this.holidays = holidays;
        this.policies = policies;
        this.linkVault = linkVault;
    }

    public CatalogService getCatalog() {
        return catalog;
    }

    public OfficePolicy policy(String officeId) {
        Office office = catalog.getOffice(officeId);
        return policies.find(officeId).orElseGet(() ->
                new OfficePolicy(officeId, SLOT_TIMES, office.getCapacityPerSlot(), Map.of(), Map.of()));
    }

    public synchronized OfficePolicy updateSchedule(String officeId, List<LocalTime> times, int capacity) {
        OfficePolicy updated = policy(officeId).withSchedule(times, capacity);
        policies.save(updated);
        return updated;
    }

    public synchronized OfficePolicy updateClosure(String officeId, LocalDate date, OfficePolicy.Closure closure) {
        if (date == null || date.isBefore(today().minusDays(1)) || date.isAfter(today().plusYears(1)))
            throw new IllegalArgumentException("Choose a closure date within the next year.");
        OfficePolicy updated = policy(officeId).withClosure(date, closure);
        policies.save(updated);
        return updated;
    }

    public synchronized OfficePolicy updateChecklist(String officeId, String serviceId,
                                                       List<String> documents, String instructions) {
        catalog.getService(serviceId);
        OfficePolicy updated = policy(officeId).withChecklist(serviceId,
                new OfficePolicy.Checklist(documents, instructions, today()));
        policies.save(updated);
        return updated;
    }

    /** Office-scoped staff view. Authorization is enforced by the API before calling this method. */
    public List<Appointment> listForOffice(String officeId, LocalDate from, LocalDate to) {
        catalog.getOffice(officeId);
        if (from == null || to == null || from.isAfter(to) || to.isAfter(from.plusMonths(12)))
            throw new IllegalArgumentException("Choose a valid date range up to one year.");
        return repository.listByOfficeRange(officeId, from, to);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The last day that can be booked: 3 months from today. */
    public LocalDate lastBookableDate() {
        return today().plusMonths(MAX_MONTHS_AHEAD);
    }

    /** Regular holidays from today to the last bookable day, when offices are closed. */
    public List<HolidayCalendar.Holiday> upcomingHolidays() {
        return holidays.between(today(), lastBookableDate());
    }

    /** Prices a service for a type of business and head count, without booking anything. */
    public Quote quote(String serviceId, String industryId, int employees) {
        PermitService service = catalog.getService(serviceId);
        return service.quote(Business.forEstimate(catalog.getIndustry(industryId), employees));
    }

    private Quote quoteForOffice(PermitService service, Business business, String officeId) {
        Quote base = service.quote(business);
        OfficePolicy.Checklist checklist = policy(officeId).checklists().get(service.getId());
        if (checklist == null || checklist.documents().isEmpty()) return base;
        List<String> requirements = new ArrayList<>(base.requirements());
        for (String document : checklist.documents()) if (!requirements.contains(document)) requirements.add(document);
        return new Quote(base.size(), base.fee(), base.inspections(), List.copyOf(requirements), base.estimatedMinutes());
    }

    public List<SlotInfo> getSlots(String officeId, LocalDate date) {
        Office office = catalog.getOffice(officeId);
        OfficePolicy policy = policy(officeId);
        validateDate(office, policy, date);
        Map<LocalTime, Long> takenByTime = repository.countActiveByTime(office.getId(), date, policy.slotTimes());
        List<SlotInfo> slots = new ArrayList<>();
        for (LocalTime time : policy.slotTimes()) {
            int taken = takenByTime.getOrDefault(time, 0L).intValue();
            int remaining = Math.max(0, policy.capacityPerSlot() - taken);
            boolean inFuture = !LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock));
            slots.add(new SlotInfo(time, policy.capacityPerSlot(), remaining, inFuture && remaining > 0));
        }
        return slots;
    }

    public synchronized BookingResult book(String serviceId, String officeId, LocalDate date, LocalTime time,
                                         Applicant applicant, Business business) {
        PermitService service = catalog.getService(serviceId);
        Office office = catalog.getOffice(officeId);

        if (!office.offers(service.getId())) {
            throw new BookingException(office.getName() + " does not offer " + service.getName().toLowerCase()
                    + ". Please choose another office.");
        }
        if (!office.serves(business.getSize())) {
            throw new BookingException(office.getName() + " does not serve "
                    + business.getSize().getLabel().toLowerCase() + " businesses. Please choose the main office.");
        }
        validateSlot(office, date, time);

        if (applicant.isVerified()
                && repository.countActiveByVerifiedId(applicant.getVerifiedId()) >= MAX_ACTIVE_PER_PERSON) {
            throw new BookingException("You already have " + MAX_ACTIVE_PER_PERSON + " active bookings. "
                    + "Cancel one on Track a booking to book another.");
        }

        String businessKey = business.getName().toLowerCase();
        boolean alreadyBooked = repository.findActiveByEmail(applicant.getEmail()).stream()
                .anyMatch(a -> a.getService().getId().equals(service.getId())
                        && a.getBusiness().getName().equalsIgnoreCase(businessKey));
        if (alreadyBooked) {
            throw new BookingException("You already have an active booking for " + service.getName().toLowerCase()
                    + " for " + business.getName() + ". Cancel it first to book a new one.");
        }

        Appointment appointment = new Appointment(newReference(date), applicant, business, service, office,
                date, time, quoteForOffice(service, business, office.getId()), Instant.now(clock));
        String managementToken = newSecret(32);
        appointment.setManagementTokenHash(hashToken(managementToken));
        appointment.setManagementTokenCiphertext(linkVault.encrypt(managementToken));
        appointment.setCheckInCode(newSecret(24));
        repository.save(appointment);
        return new BookingResult(appointment, managementToken);
    }

    public Appointment find(String referenceNo) {
        return repository.findByReference(referenceNo)
                .orElseThrow(() -> new NotFoundException("No booking found for that reference number."));
    }

    /** A booking reference is public and never authorizes an applicant-facing lookup or change. */
    public Appointment findManaged(String referenceNo, String managementToken) {
        String error = "Booking not found or private access code is invalid.";
        if (managementToken == null || !managementToken.matches("[A-Za-z0-9_-]{43}"))
            throw new NotFoundException(error);
        Appointment a = repository.findByReference(referenceNo).orElseThrow(() -> new NotFoundException(error));
        String expected = a.getManagementTokenHash();
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                hashToken(managementToken).getBytes(StandardCharsets.US_ASCII)))
            throw new NotFoundException(error);
        return a;
    }

    /** An opaque QR code only locates a booking for a separately authenticated office worker. */
    public Appointment findByCheckInCode(String checkInCode) {
        return repository.findByCheckInCode(checkInCode)
                .orElseThrow(() -> new NotFoundException("No booking found for that check-in code."));
    }

    /** Staff-assisted recovery for existing bookings and lost private codes. Revokes the old code. */
    public synchronized BookingResult resetManagement(String referenceNo) {
        Appointment a = find(referenceNo);
        String managementToken = newSecret(32);
        a.setManagementTokenHash(hashToken(managementToken));
        a.setManagementTokenCiphertext(linkVault.encrypt(managementToken));
        if (a.getCheckInCode() == null) a.setCheckInCode(newSecret(24));
        repository.save(a);
        return new BookingResult(a, managementToken);
    }

    public synchronized Appointment cancel(String referenceNo, String managementToken) {
        Appointment appointment = findManaged(referenceNo, managementToken);
        appointment.cancel();
        repository.save(appointment);
        return appointment;
    }

    public synchronized Appointment reschedule(String referenceNo, LocalDate date, LocalTime time, String managementToken) {
        Appointment appointment = findManaged(referenceNo, managementToken);
        if (!appointment.isActive()) {
            throw new BookingException("A cancelled booking cannot be moved.");
        }
        if (date.equals(appointment.getDate()) && time.equals(appointment.getTime())) {
            throw new BookingException("Please choose a different date or time.");
        }
        validateSlot(appointment.getOffice(), date, time);
        appointment.moveTo(date, time);
        repository.save(appointment);
        return appointment;
    }

    /** District-head initiated move, with the same capacity and acceptance protections as applicant moves. */
    public synchronized Appointment moveAtOffice(String referenceNo, LocalDate date, LocalTime time) {
        Appointment appointment = find(referenceNo);
        if (!appointment.isActive() || appointment.getAcceptedAt() != null || appointment.isWalkIn())
            throw new BookingException("Only an unaccepted scheduled booking can be moved.");
        if (date.equals(appointment.getDate()) && time.equals(appointment.getTime()))
            throw new BookingException("Choose a different date or time.");
        validateSlot(appointment.getOffice(), date, time);
        appointment.moveTo(date, time);
        repository.save(appointment);
        return appointment;
    }

    // ---- rules ----

    private void validateDate(Office office, OfficePolicy policy, LocalDate date) {
        if (date == null) {
            throw new BookingException("Please choose a date.");
        }
        LocalDate today = today();
        if (date.isBefore(today)) {
            throw new BookingException("That date has already passed.");
        }
        if (date.isAfter(lastBookableDate())) {
            throw new BookingException("Slots open up to " + MAX_MONTHS_AHEAD + " months in advance, until "
                    + lastBookableDate().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH)) + ".");
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new BookingException("Offices are closed on weekends. Please choose a weekday.");
        }
        holidays.holidayOn(date).ifPresent(h -> {
            throw new BookingException("Offices are closed on " + h.name() + ", a regular holiday. Please choose another day.");
        });
        OfficePolicy.Closure closure = policy.closures().get(date);
        if (closure != null) throw new BookingException(office.getName() + " is closed: " + closure.reason());
    }

    private void validateSlot(Office office, LocalDate date, LocalTime time) {
        OfficePolicy policy = policy(office.getId());
        validateDate(office, policy, date);
        if (time == null || !policy.slotTimes().contains(time)) {
            throw new BookingException("Please choose one of the available time slots.");
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))) {
            throw new BookingException("That time has already passed.");
        }
        long taken = repository.countActive(office.getId(), date, time);
        if (taken >= policy.capacityPerSlot()) {
            throw new BookingException("That time slot is full. Please pick another.");
        }
    }

    // ---- month calendar ----

    /** One day on the booking calendar: whether the office is open, why not, and places left across its slots. */
    public record DayAvailability(LocalDate date, boolean open, String closedReason, int remaining, int capacity) {}

    /** Every day of a month for an office: closed days with their reason, open days with places left. */
    public List<DayAvailability> monthAvailability(String officeId, java.time.YearMonth month) {
        Office office = catalog.getOffice(officeId);
        OfficePolicy policy = policy(officeId);
        LocalDate first = month.atDay(1), last = month.atEndOfMonth();
        Map<LocalDate, Map<LocalTime, Long>> taken = repository.countActiveByDateTime(office.getId(), first, last, policy.slotTimes());
        LocalDate today = today();
        LocalDateTime now = LocalDateTime.now(clock);
        int cap = policy.capacityPerSlot();
        List<DayAvailability> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            String reason = null;
            if (d.isBefore(today)) reason = "Past";
            else if (d.isAfter(lastBookableDate())) reason = "Not open for booking yet";
            else if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) reason = "Weekend";
            else {
                java.util.Optional<HolidayCalendar.Holiday> h = holidays.holidayOn(d);
                if (h.isPresent()) reason = h.get().name();
            }
            if (reason == null && policy.closures().containsKey(d)) reason = policy.closures().get(d).reason();
            int remaining = 0, capacity = 0;
            if (reason == null) {
                Map<LocalTime, Long> byTime = taken.getOrDefault(d, Map.of());
                for (LocalTime t : policy.slotTimes()) {
                    if (LocalDateTime.of(d, t).isBefore(now)) continue;   // slots already gone today
                    capacity += cap;
                    remaining += Math.max(0, cap - byTime.getOrDefault(t, 0L).intValue());
                }
                if (capacity == 0) reason = "No slots left today";
            }
            days.add(new DayAvailability(d, reason == null, reason, remaining, capacity));
        }
        return days;
    }

    // ---- demo fee payment (no money moves) ----

    /** The ways to pay a real LGU e-payment page usually offers; here they only label the demo receipt. */
    public static final Map<String, String> PAYMENT_CHANNELS = paymentChannels();

    private static Map<String, String> paymentChannels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("qrph", "QR Ph");
        m.put("ewallet", "E-wallet");
        m.put("online-banking", "Online banking");
        m.put("landbank", "LANDBANK Link.BizPortal");
        return m;
    }

    /** What the applicant owes: the booking's fee snapshot scaled by its city's sample rate, rounded like the page. */
    public double amountDue(Appointment a) {
        double rate = SampleCityRates.of(a.getOffice().getId());
        double total = 0;
        for (FeeLine line : a.getQuote().fee().getLines()) {
            total += Math.round(line.amount() * rate * 100) / 100.0;
        }
        return Math.round(total * 100) / 100.0;
    }

    /** Starts (or restarts) a demo payment for a confirmed, unpaid booking through the chosen channel. */
    public synchronized Appointment startPayment(String referenceNo, String channelId, String managementToken) {
        Appointment a = findManaged(referenceNo, managementToken);
        if (!a.isActive()) throw new BookingException("A cancelled booking can't be paid.");
        if (a.getPayment() != null && a.getPayment().isPaid()) throw new BookingException("This booking is already paid.");
        String channel = PAYMENT_CHANNELS.get(channelId);
        if (channel == null) throw new BookingException("Choose how you'd like to pay.");
        String txn = a.getReferenceNo() + "-P" + randomCode(16);
        a.setPayment(new Payment(txn, channel, amountDue(a), Instant.now(clock)));
        repository.save(a);
        return a;
    }

    /** Office check-in is idempotent; payment remains a separate, live status. */
    public synchronized Appointment acceptAtOffice(String referenceNo) {
        Appointment a = find(referenceNo);
        if (!a.isActive()) throw new BookingException("This booking cannot be accepted.");
        return repository.acceptAtOffice(a, Instant.now(clock));
    }

    public synchronized Appointment markNoShow(String referenceNo) {
        Appointment a = find(referenceNo);
        if (LocalDateTime.now(clock).isBefore(a.getDate().atTime(a.getTime()).plusMinutes(30)))
            throw new BookingException("Wait at least 30 minutes after the appointment time before marking a no-show.");
        a.markNoShow(Instant.now(clock));
        repository.save(a);
        return a;
    }

    /** Treasurer-verified city hall payment. Online demo payments remain a separate record. */
    public synchronized Appointment recordCounterPayment(String referenceNo, double amount,
                                                           String officialReceiptNo, String treasurer) {
        Appointment a = find(referenceNo);
        if (!a.isActive()) throw new BookingException("Payment cannot be recorded for a closed booking.");
        if (a.getCounterPayment() != null)
            throw new BookingException("A city hall payment is already recorded for this booking.");
        if (officialReceiptNo != null && repository.officialReceiptExists(a.getOffice().getId(), officialReceiptNo.trim()))
            throw new BookingException("This official receipt number is already recorded for this office.");
        a.setCounterPayment(new CounterPayment(amount, officialReceiptNo, Instant.now(clock), treasurer));
        repository.save(a);
        return a;
    }

    /** Records an arriving visitor without consuming an online appointment slot. */
    public synchronized Appointment createWalkIn(String serviceId, String officeId, Applicant applicant, Business business) {
        PermitService service = catalog.getService(serviceId);
        Office office = catalog.getOffice(officeId);
        if (!office.offers(serviceId) || !office.serves(business.getSize()))
            throw new BookingException("This office does not serve the chosen service or business size.");
        LocalDate day = today();
        LocalTime arrivedAt = LocalTime.now(clock).withSecond(0).withNano(0);
        Appointment a = new Appointment(newReference(day), applicant, business, service, office,
                day, arrivedAt, quoteForOffice(service, business, officeId), Instant.now(clock));
        a.setWalkIn(true);
        repository.save(a);
        return repository.acceptAtOffice(a, Instant.now(clock));
    }

    /** Admin-only demo data: fill every remaining slot on an open day with clearly synthetic records. */
    public synchronized int seedFullyBookedDay(String officeId, LocalDate date) {
        Office office = catalog.getOffice(officeId);
        OfficePolicy policy = policy(officeId);
        validateDate(office, policy, date);
        if ((long) policy.slotTimes().size() * policy.capacityPerSlot() > 200)
            throw new BookingException("For a demo seed, set no more than 200 total daily places.");
        int created = 0;
        for (LocalTime time : policy.slotTimes()) {
            if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))) continue;
            while (repository.countActive(officeId, date, time) < policy.capacityPerSlot()) {
                int number = ++created;
                String suffix = date.toString().replace("-", "") + "-" + time.toString().replace(":", "") + "-" + number;
                Applicant applicant = new Applicant("Demo Visitor " + number,
                        "demo-" + officeId + "-" + suffix + "@example.invalid", "09123456789");
                Business business = new Business("Demo Business " + suffix, "Simulation record only, " + office.getName(),
                        catalog.getIndustry("retail"), 2);
                Appointment a = new Appointment(newReference(date), applicant, business,
                        catalog.getService("new-permit"), office, date, time,
                        quoteForOffice(catalog.getService("new-permit"), business, officeId), Instant.now(clock));
                String access = newSecret(32);
                a.setManagementTokenHash(hashToken(access));
                a.setCheckInCode(newSecret(24));
                a.setDemoRecord(true);
                repository.save(a);
            }
        }
        return created;
    }

    /** The booking a payment transaction belongs to. Transaction ids are the booking reference plus "-P" and a code. */
    public Appointment findByPaymentTxn(String txn) {
        String t = txn == null ? "" : txn.trim().toUpperCase();
        int cut = t.lastIndexOf("-P");
        Appointment a = cut > 0 ? repository.findByReference(t.substring(0, cut)).orElse(null) : null;
        if (a == null || a.getPayment() == null || !a.getPayment().getTransactionId().equals(t)) {
            throw new NotFoundException("This payment link isn't valid or has expired.");
        }
        return a;
    }

    /** Marks a demo payment paid and issues a demo acknowledgement receipt number. Paying twice changes nothing. */
    public synchronized Appointment completePayment(String txn) {
        Appointment a = findByPaymentTxn(txn);
        if (!a.isActive()) throw new BookingException("This booking was cancelled, so it can't be paid.");
        Payment p = a.getPayment();
        if (!p.isPaid()) {
            LocalDate today = today();
            p.markPaid("AR-DEMO-" + today.toString().replace("-", "") + "-" + randomCode(5), Instant.now(clock));
            repository.save(a);
        }
        return a;
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
        return sb.toString();
    }

    private String newSecret(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot create booking access code.", e);
        }
    }

    private String newReference(LocalDate date) {
        String ref;
        do {
            StringBuilder sb = new StringBuilder("BPL-")
                    .append(date.toString().replace("-", "")).append('-');
            for (int i = 0; i < 5; i++) {
                sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
            }
            ref = sb.toString();
        } while (repository.findByReference(ref).isPresent());
        return ref;
    }
}
