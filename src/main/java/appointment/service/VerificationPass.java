package appointment.service;

import appointment.util.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A short-lived, tamper-proof pass saying "this browser verified as this person" (via eGovPH or an ID scan).
 * The booking must carry it, and the booker's name and ID are read from it, never from the form.
 * It's HMAC-signed and holds no state on the server, so it survives restarts and works across instances.
 */
public class VerificationPass {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final byte[] key;
    private final Clock clock;

    /** {@code secret} is any server-only secret; the pass key is derived from it. */
    public VerificationPass(String secret, Clock clock) {
        this.key = ("metromaya-verify-pass:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue(VerifiedPerson p) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("id", p.id());
        claims.put("name", p.fullName());
        claims.put("method", p.method());
        claims.put("exp", clock.instant().plus(TTL).getEpochSecond());
        String payload = b64(Json.stringify(claims).getBytes(StandardCharsets.UTF_8));
        return payload + "." + b64(sign(payload));
    }

    /** The person a pass was issued to; throws if it's missing, forged or expired. */
    @SuppressWarnings("unchecked")
    public VerifiedPerson check(String pass) {
        String again = "Please verify again (eGovPH or ID scan) before booking.";
        if (pass == null || pass.isBlank()) {
            throw new BookingException("Verify with eGovPH or scan your ID before booking.");
        }
        int dot = pass.indexOf('.');
        if (dot < 1) throw new BookingException(again);
        String payload = pass.substring(0, dot);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(pass.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw new BookingException(again);
        }
        if (!MessageDigest.isEqual(given, sign(payload))) throw new BookingException(again);

        Map<String, Object> claims = (Map<String, Object>) Json.parse(
                new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
        if (clock.instant().getEpochSecond() > ((Number) claims.get("exp")).longValue()) {
            throw new BookingException("Your verification expired. " + again);
        }
        return new VerifiedPerson((String) claims.get("id"), (String) claims.get("name"), null, null,
                (String) claims.get("method"), null);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Verification is not available right now.", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
