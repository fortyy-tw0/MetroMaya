package appointment.service;

import appointment.model.Office;
import appointment.model.StaffAccount;
import appointment.repository.StaffAccountRepository;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Password login and stateless, signed eight-hour staff sessions. */
public class StaffAuthService {
    public static class AccessDenied extends RuntimeException {
        private final int status;
        public AccessDenied(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }

    private static final int PASSWORD_ITERATIONS = 180_000;
    private static final long SESSION_SECONDS = 8 * 60 * 60;
    private final StaffAccountRepository accounts;
    private final CatalogService catalog;
    private final Clock clock;
    private final String adminUsername;
    private final String adminPassword;
    private final byte[] sessionKey;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();
    private record Failure(int count, long firstAt) {}
    public record Login(String token, StaffAccount account) {}

    public StaffAuthService(StaffAccountRepository accounts, CatalogService catalog, Clock clock,
                            String adminUsername, String adminPassword, String sessionSecret) {
        this.accounts = accounts;
        this.catalog = catalog;
        this.clock = clock;
        this.adminUsername = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim().toLowerCase();
        if (!this.adminUsername.matches("[a-z0-9][a-z0-9._-]{2,39}"))
            throw new IllegalArgumentException("STAFF_ADMIN_USERNAME has an invalid format.");
        this.adminPassword = adminPassword;
        this.sessionKey = sessionSecret == null || sessionSecret.length() < 32 ? null
                : sessionSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean configured() { return adminPassword != null && !adminPassword.isBlank() && sessionKey != null; }

    public Login login(String username, String password) {
        if (!configured()) throw new AccessDenied(503, "Staff login needs STAFF_ADMIN_PASSWORD and STAFF_SESSION_SECRET on the server.");
        String name = username == null ? "" : username.trim().toLowerCase();
        long now = Instant.now(clock).getEpochSecond();
        StaffAccount account = account(name);
        String failureKey = account == null ? "_unknown" : name;
        Failure previous = failures.get(failureKey);
        if (previous != null && previous.count() >= 5 && now - previous.firstAt() < 15 * 60)
            throw new AccessDenied(429, "Too many sign-in attempts. Try again in 15 minutes.");
        boolean valid = account != null && account.active() && password != null
                && (name.equals(adminUsername) ? constantEquals(adminPassword, password)
                : verifyPassword(password, account.passwordHash()));
        if (!valid) {
            failures.compute(failureKey, (ignored, f) -> f == null || now - f.firstAt() >= 15 * 60
                    ? new Failure(1, now) : new Failure(f.count() + 1, f.firstAt()));
            throw new AccessDenied(401, "Username or password is incorrect.");
        }
        failures.remove(failureKey);
        long expiry = now + SESSION_SECONDS;
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (name + ":" + expiry + ":" + credentialVersion(account)).getBytes(StandardCharsets.UTF_8));
        String unsigned = payload + "." + randomCode(12);
        return new Login(unsigned + "." + sign(unsigned), account);
    }

    public StaffAccount authenticate(String authorization) {
        if (!configured()) throw new AccessDenied(503, "Staff login is not configured on this server.");
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new AccessDenied(401, "Sign in to continue.");
        String token = authorization.substring(7);
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !constantEquals(sign(parts[0] + "." + parts[1]), parts[2]))
            throw new AccessDenied(401, "Your staff session is invalid. Sign in again.");
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] claims = payload.split(":", -1);
            if (claims.length != 3 || Long.parseLong(claims[1]) <= Instant.now(clock).getEpochSecond())
                throw new IllegalArgumentException();
            StaffAccount account = account(claims[0]);
            if (account == null || !account.active() || !constantEquals(claims[2], credentialVersion(account)))
                throw new IllegalArgumentException();
            return account;
        } catch (IllegalArgumentException e) {
            throw new AccessDenied(401, "Your staff session has expired. Sign in again.");
        }
    }

    public void requireRole(StaffAccount account, StaffAccount.Role... roles) {
        for (StaffAccount.Role role : roles) if (account.role() == role) return;
        throw new AccessDenied(403, "Your role cannot use this feature.");
    }

    public String officeFor(StaffAccount account, String requestedOffice) {
        if (account.role() == StaffAccount.Role.ADMIN) {
            if (requestedOffice == null || requestedOffice.isBlank())
                throw new IllegalArgumentException("Choose an office.");
            return catalog.getOffice(requestedOffice).getId();
        }
        if (requestedOffice != null && !requestedOffice.isBlank() && !account.officeId().equals(requestedOffice))
            throw new AccessDenied(403, "This booking belongs to another office.");
        return account.officeId();
    }

    public void requireOffice(StaffAccount account, String officeId) {
        if (account.role() != StaffAccount.Role.ADMIN && !account.officeId().equals(officeId))
            throw new AccessDenied(404, "Booking not found for your office.");
    }

    public StaffAccount create(String username, String password, StaffAccount.Role role, String officeId) {
        if (password == null || password.length() < 12)
            throw new IllegalArgumentException("Use a password of at least 12 characters.");
        String name = username == null ? "" : username.trim().toLowerCase();
        if (name.equals(adminUsername) || accounts.find(name).isPresent())
            throw new IllegalArgumentException("That username is already in use.");
        String office = role == StaffAccount.Role.ADMIN ? null : catalog.getOffice(officeId).getId();
        StaffAccount account = new StaffAccount(name, hashPassword(password), role, office, true);
        accounts.save(account);
        return account;
    }

    /** Idempotent provisioning of a distinct district-head account for every Metro Manila office. */
    public List<Map<String, String>> seedDistrictHeads() {
        List<Map<String, String>> issued = new ArrayList<>();
        for (Office office : catalog.getOffices()) {
            String username = "head." + office.getId();
            if (accounts.find(username).isPresent()) continue;
            String password = randomCode(24);
            create(username, password, StaffAccount.Role.DISTRICT_HEAD, office.getId());
            Map<String, String> row = new LinkedHashMap<>();
            row.put("username", username);
            row.put("officeId", office.getId());
            row.put("temporaryPassword", password);
            issued.add(row);
        }
        return issued;
    }

    public List<Map<String, Object>> users() {
        return accounts.all().stream().map(StaffAuthService::publicAccount).toList();
    }

    public StaffAccount setActive(String username, boolean active) {
        StaffAccount existing = managedAccount(username);
        StaffAccount updated = new StaffAccount(existing.username(), existing.passwordHash(),
                existing.role(), existing.officeId(), active);
        accounts.save(updated);
        return updated;
    }

    public StaffAccount resetPassword(String username, String newPassword) {
        if (newPassword == null || newPassword.length() < 12)
            throw new IllegalArgumentException("Use a password of at least 12 characters.");
        StaffAccount existing = managedAccount(username);
        StaffAccount updated = new StaffAccount(existing.username(), hashPassword(newPassword),
                existing.role(), existing.officeId(), existing.active());
        accounts.save(updated);
        return updated;
    }

    private StaffAccount managedAccount(String username) {
        String name = username == null ? "" : username.trim().toLowerCase();
        if (name.equals(adminUsername)) throw new IllegalArgumentException("Change the bootstrap admin password in the server environment.");
        return accounts.find(name).orElseThrow(() -> new NotFoundException("Staff account not found."));
    }

    private String credentialVersion(StaffAccount account) {
        return digest(account.username().equals(adminUsername) ? adminPassword : account.passwordHash()).substring(0, 20);
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot hash staff credential version.", e); }
    }

    public static Map<String, Object> publicAccount(StaffAccount account) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("username", account.username());
        out.put("role", account.role().name());
        out.put("officeId", account.officeId());
        out.put("active", account.active());
        return out;
    }

    private StaffAccount account(String username) {
        if (username.equals(adminUsername))
            return new StaffAccount(adminUsername, "", StaffAccount.Role.ADMIN, null, true);
        return accounts.find(username).orElse(null);
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] digest = derive(password, salt, PASSWORD_ITERATIONS);
        return "pbkdf2$" + PASSWORD_ITERATIONS + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static boolean verifyPassword(String password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (Exception e) { return false; }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Password hashing is unavailable.", e); }
        finally { spec.clearPassword(); }
    }

    private String randomCode(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sessionKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Staff sessions are unavailable.", e); }
    }

    private static boolean constantEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
