package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.FeedbackProperties;
import pl.seniordeveloper.pulsedigest.shared.util.HmacSigner;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackLinkBuilderTest {

    private static final String RECEIVER = "https://fb.example/vote";
    private static final String EDITION = "2026-07-10";

    private static FeedbackProperties props(String receiverUrl, String secret) {
        return new FeedbackProperties(true, 30, receiverUrl, secret);
    }

    @Test
    void rendersNothingWhenNoReceiverIsConfigured() {
        assertThat(FeedbackLinkBuilder.render("https://a", "GitHub", "AI/LLM", EDITION, props("", "s"))).isEmpty();
    }

    @Test
    void rendersUnsignedLinksWhenNoSigningSecretIsSet() {
        // The flag: links keep working against today's receiver until the new one is deployed.
        String html = FeedbackLinkBuilder.render("https://a", "GitHub", "AI/LLM", EDITION, props(RECEIVER, ""));

        assertThat(html).contains("vote=up").contains("vote=down").doesNotContain("sig=");
    }

    @Test
    void signsEachVoteSeparatelyWhenASecretIsSet() {
        String html = FeedbackLinkBuilder.render("https://a", "GitHub", "AI/LLM", EDITION, props(RECEIVER, "secret"));

        assertThat(html).contains("sig=" + HmacSigner.sign("https://a|up|GitHub|AI/LLM|" + EDITION, "secret"));
        assertThat(html).contains("sig=" + HmacSigner.sign("https://a|down|GitHub|AI/LLM|" + EDITION, "secret"));
    }

    @Test
    void carriesTheCategorySoAVoteCanPunishTheTopicRatherThanTheWholeSource() {
        String html = FeedbackLinkBuilder.render("https://a", "arXiv/cs.AI", "Research", EDITION,
                props(RECEIVER, ""));

        assertThat(html).contains("category=Research");
    }

    @Test
    void theSignatureCoversTheCategoryToo() {
        // Anything that changes what the receiver writes must be inside the signature.
        String withCategory = FeedbackLinkBuilder.render("https://a", "GitHub", "Research", EDITION,
                props(RECEIVER, "secret"));
        String withOther = FeedbackLinkBuilder.render("https://a", "GitHub", "AI/LLM", EDITION,
                props(RECEIVER, "secret"));

        assertThat(withCategory).isNotEqualTo(withOther);
        assertThat(withCategory).contains(HmacSigner.sign("https://a|up|GitHub|Research|" + EDITION, "secret"));
    }

    @Test
    void carriesTheEditionSoTheReceiverCanEnforceOneVotePerItemPerEdition() {
        String html = FeedbackLinkBuilder.render("https://a", "GitHub", "AI/LLM", EDITION, props(RECEIVER, ""));

        assertThat(html).contains("edition=" + EDITION);
    }

    @Test
    void urlEncodesTheItemUrlAndSourceButSignsTheirRawValues() {
        // The signature must be computed over what the reader clicked on, not over its encoding.
        String itemUrl = "https://example.com/a?x=1&y=2";
        String html = FeedbackLinkBuilder.render(itemUrl, "RSS/Java", "Java/JVM", EDITION, props(RECEIVER, "secret"));

        assertThat(html).contains("url=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1%26y%3D2");
        assertThat(html).contains("source=RSS%2FJava");
        assertThat(html).contains(HmacSigner.sign(itemUrl + "|up|RSS/Java|Java/JVM|" + EDITION, "secret"));
    }

    @Test
    void aSignatureIsItselfUrlEncodedSoItSurvivesTheQueryString() {
        String html = FeedbackLinkBuilder.render("https://a", "GitHub", "AI/LLM", EDITION, props(RECEIVER, "secret"));

        assertThat(html).doesNotContain("sig=+").doesNotContain("sig=/");
    }

    @Test
    void handlesNullItemUrlAndSourceWithoutBlowingUp() {
        assertThat(FeedbackLinkBuilder.render(null, null, null, EDITION, props(RECEIVER, "secret")))
                .contains("vote=up");
    }
}
