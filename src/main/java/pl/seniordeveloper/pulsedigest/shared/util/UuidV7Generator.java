package pl.seniordeveloper.pulsedigest.shared.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 generator based on RFC 9562. Uses Unix epoch milliseconds and random bits.
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
        // Utility class
    }

    public static UUID next() {
        long epochMillis = System.currentTimeMillis();
        long randA = RANDOM.nextLong(1L << 12);
        long msb = ((epochMillis & 0x0000_FFFF_FFFF_FFFFL) << 16) | (0x7L << 12) | randA;

        long randB = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        long lsb = 0x8000_0000_0000_0000L | randB;

        return new UUID(msb, lsb);
    }
}
