package pl.seniordeveloper.pulsedigest.shared.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA256 over a canonical message, rendered as an unpadded URL-safe Base64 string.
 *
 * <p>Used to sign the feedback links in the digest email. The signature does not stop a mail scanner
 * from following a link — it is right there in the href — but it does mean a vote cannot be forged or
 * edited: flipping {@code vote=up} to {@code vote=down}, or pointing the link at another item, breaks
 * the signature and the external receiver rejects the write.
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    /**
     * @param message canonical, delimiter-joined payload (e.g. {@code url|vote|source|edition})
     * @param secret  shared secret, also known to the receiver; must not be blank
     * @throws IllegalArgumentException when the secret is absent — signing with an empty key would
     *                                  produce a valid-looking signature anyone could reproduce
     */
    public static String sign(String message, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Refusing to sign with a blank secret");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] signature = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable on this JVM", e);
        }
    }
}
