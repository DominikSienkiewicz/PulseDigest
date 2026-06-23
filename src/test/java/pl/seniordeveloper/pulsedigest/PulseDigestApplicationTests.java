package pl.seniordeveloper.pulsedigest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires real API keys — run manually with TWITTER_BEARER_TOKEN etc.")
@SpringBootTest
class PulseDigestApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: a passing run proves the Spring context boots and all beans wire.
    }

}
