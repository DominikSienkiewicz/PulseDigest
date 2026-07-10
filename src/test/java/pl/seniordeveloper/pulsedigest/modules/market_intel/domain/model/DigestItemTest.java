package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigestItemTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesWhyItMattersFromSnakeCaseJson() throws Exception {
        String json = """
                {
                  "title": "Spring Boot 4.1",
                  "url": "https://example.com",
                  "source": "GitHub Releases",
                  "category": "Java/JVM",
                  "type": "RELEASE",
                  "score": 9,
                  "engagement_score": 100,
                  "summary": "New release",
                  "why_it_matters": "Upgrade unlocks virtual-thread pinning fixes"
                }
                """;

        DigestItem item = objectMapper.readValue(json, DigestItem.class);

        assertThat(item.whyItMatters()).isEqualTo("Upgrade unlocks virtual-thread pinning fixes");
    }

    @Test
    void deserializesTopicKeyFromSnakeCaseJson() throws Exception {
        String json = """
                {
                  "title": "t", "url": "https://x", "category": "AI/LLM", "type": "RELEASE",
                  "score": 9, "summary": "s", "topic_key": "model-context-protocol"
                }
                """;

        DigestItem item = objectMapper.readValue(json, DigestItem.class);

        assertThat(item.topicKey()).isEqualTo("model-context-protocol");
    }

    @Test
    void nineArgConstructorLeavesTopicKeyNull() {
        DigestItem item = new DigestItem("t", "https://x", "GitHub", "Java", "RELEASE", 8, 50, "s", null);

        assertThat(item.topicKey()).isNull();
    }

    @Test
    void whyItMattersIsNullWhenAbsentFromJson() throws Exception {
        String json = """
                {
                  "title": "t", "url": "https://x", "source": "GitHub",
                  "category": "Java", "type": "RELEASE", "score": 8,
                  "engagement_score": 50, "summary": "s"
                }
                """;

        DigestItem item = objectMapper.readValue(json, DigestItem.class);

        assertThat(item.whyItMatters()).isNull();
    }
}
