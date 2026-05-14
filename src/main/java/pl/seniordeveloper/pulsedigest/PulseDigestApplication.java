package pl.seniordeveloper.pulsedigest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ReportProperties;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.TwitterProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ReportProperties.class, TwitterProperties.class})
public final class PulseDigestApplication {

    private PulseDigestApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(PulseDigestApplication.class, args);
    }
}
