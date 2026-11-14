package appointment.service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts management tokens only when notification reminders need to recover a private link. */
public class ManagementLinkVault {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public ManagementLinkVault(String secret) {
        if (secret == null || secret.length() < 32) { key = null; return; }
        try { key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException("Cannot initialize private-link encryption.", e); }
    }

    public boolean configured() { return key != null; }

    public String encrypt(String token) {
        if (!configured()) return null;
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.US_ASCII));
            byte[] data = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, data, 0, nonce.length);
            System.arraycopy(encrypted, 0, data, nonce.length, encrypted.length);
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        } catch (Exception e) { throw new IllegalStateException("Cannot encrypt private booking access.", e); }
    }

    public String decrypt(String stored) {
        if (!configured() || stored == null || !stored.startsWith("v1.")) return null;
        try {
            byte[] data = Base64.getUrlDecoder().decode(stored.substring(3));
            byte[] nonce = java.util.Arrays.copyOfRange(data, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(data, 12, data.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.US_ASCII);
        } catch (Exception e) { throw new IllegalStateException("Cannot decrypt private booking access.", e); }
    }
}
