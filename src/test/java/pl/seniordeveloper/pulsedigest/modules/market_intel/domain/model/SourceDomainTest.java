package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDomainTest {

    @Test
    void arxivPrefixResolvesToScience() {
        assertThat(SourceDomain.from("arXiv/cs.AI")).isEqualTo(SourceDomain.SCIENCE);
        assertThat(SourceDomain.from("arXiv/cs.LG")).isEqualTo(SourceDomain.SCIENCE);
    }

    @Test
    void huggingFaceResolvesToScience() {
        assertThat(SourceDomain.from("Hugging Face")).isEqualTo(SourceDomain.SCIENCE);
    }

    @Test
    void githubReposAndReleasesResolveToCode() {
        assertThat(SourceDomain.from("GitHub")).isEqualTo(SourceDomain.CODE);
        assertThat(SourceDomain.from("GitHub Releases")).isEqualTo(SourceDomain.CODE);
        assertThat(SourceDomain.from("OpenJDK JEP")).isEqualTo(SourceDomain.CODE);
        assertThat(SourceDomain.from("CNCF Landscape")).isEqualTo(SourceDomain.CODE);
    }

    @Test
    void hackerNewsProductHuntTechRadarResolveToBusiness() {
        assertThat(SourceDomain.from("Hacker News")).isEqualTo(SourceDomain.BUSINESS);
        assertThat(SourceDomain.from("Hacker News/ask")).isEqualTo(SourceDomain.BUSINESS);
        assertThat(SourceDomain.from("Product Hunt")).isEqualTo(SourceDomain.BUSINESS);
        assertThat(SourceDomain.from("Tech Radar")).isEqualTo(SourceDomain.BUSINESS);
        assertThat(SourceDomain.from("YouTube/SpringOne")).isEqualTo(SourceDomain.BUSINESS);
    }

    @Test
    void twitterRedditRssResolveToSocial() {
        assertThat(SourceDomain.from("Twitter/X")).isEqualTo(SourceDomain.SOCIAL);
        assertThat(SourceDomain.from("Reddit/r/java")).isEqualTo(SourceDomain.SOCIAL);
        assertThat(SourceDomain.from("RSS/techcrunch")).isEqualTo(SourceDomain.SOCIAL);
    }

    @Test
    void securitySourcesResolveToSecurity() {
        assertThat(SourceDomain.from("Security Advisories")).isEqualTo(SourceDomain.SECURITY);
    }

    @Test
    void labAnnouncementSourcesResolveToLabs() {
        // A dedicated bucket rather than SCIENCE: an official lab post, an arXiv paper and a GitHub
        // repo are three genuinely different kinds of confirmation — which is what the rule measures.
        assertThat(SourceDomain.from("Anthropic News")).isEqualTo(SourceDomain.LABS);
        assertThat(SourceDomain.from("Anthropic Engineering")).isEqualTo(SourceDomain.LABS);
        assertThat(SourceDomain.from("Claude Blog")).isEqualTo(SourceDomain.LABS);
        assertThat(SourceDomain.from("Google Gemini Blog")).isEqualTo(SourceDomain.LABS);
        assertThat(SourceDomain.from("OpenAI Dev Blog")).isEqualTo(SourceDomain.LABS);
    }

    @Test
    void openJdkJepStaysInCodeDespiteTheOpenAiLabPrefix() {
        assertThat(SourceDomain.from("OpenJDK JEP")).isEqualTo(SourceDomain.CODE);
    }

    @Test
    void nullResolvesToSocial() {
        assertThat(SourceDomain.from(null)).isEqualTo(SourceDomain.SOCIAL);
    }

    @Test
    void unknownSourceResolvesToSocial() {
        assertThat(SourceDomain.from("unknown-source")).isEqualTo(SourceDomain.SOCIAL);
    }
}
