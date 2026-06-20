package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailFormattingTest {

    @Test
    void escapesHtmlReservedCharacters() {
        assertThat(EmailFormatting.escapeHtml("<b>&\"x</b>")).isEqualTo("&lt;b&gt;&amp;&quot;x&lt;/b&gt;");
    }

    @Test
    void escapesHtmlOnNullReturnsEmpty() {
        assertThat(EmailFormatting.escapeHtml(null)).isEmpty();
    }

    @Test
    void typeBadgeUsesPaletteForKnownType() {
        String badge = EmailFormatting.typeBadge("INCIDENT");
        assertThat(badge).contains("background:#fee2e2").contains("color:#b91c1c").contains(">INCIDENT<");
    }

    @Test
    void typeBadgeFallsBackToNeutralPaletteForUnknownType() {
        String badge = EmailFormatting.typeBadge("MYSTERY");
        assertThat(badge).contains("background:#f1f5f9").contains("color:#475569");
    }

    @Test
    void typeBadgeUsesDistinctPaletteForPromotion() {
        String badge = EmailFormatting.typeBadge("PROMOTION");
        assertThat(badge).contains("background:#fae8ff").contains("color:#a21caf").contains(">PROMOTION<");
    }

    @Test
    void safeHrefAllowsHttpAndHttpsUrls() {
        assertThat(EmailFormatting.safeHref("https://example.com/a?b=1")).isEqualTo("https://example.com/a?b=1");
        assertThat(EmailFormatting.safeHref("http://example.com")).isEqualTo("http://example.com");
    }

    @Test
    void safeHrefRejectsDangerousSchemes() {
        assertThat(EmailFormatting.safeHref("javascript:alert(1)")).isEqualTo("#");
        assertThat(EmailFormatting.safeHref("JavaScript:alert(1)")).isEqualTo("#");
        assertThat(EmailFormatting.safeHref("data:text/html,<script>")).isEqualTo("#");
        assertThat(EmailFormatting.safeHref(" vbscript:msgbox ")).isEqualTo("#");
        assertThat(EmailFormatting.safeHref("mailto:a@b.com")).isEqualTo("#");
    }

    @Test
    void safeHrefEscapesHtmlInOtherwiseValidUrl() {
        assertThat(EmailFormatting.safeHref("https://e.com/?q=\"><img>"))
                .isEqualTo("https://e.com/?q=&quot;&gt;&lt;img&gt;");
    }

    @Test
    void safeHrefReturnsHashForNullOrBlank() {
        assertThat(EmailFormatting.safeHref(null)).isEqualTo("#");
        assertThat(EmailFormatting.safeHref("   ")).isEqualTo("#");
    }

    @Test
    void formatNumberRendersThousandsWithOneDecimal() {
        assertThat(EmailFormatting.formatNumber(1500)).isEqualTo("1.5k");
    }

    @Test
    void formatNumberDropsDecimalAtOrAboveTenK() {
        assertThat(EmailFormatting.formatNumber(42_000)).isEqualTo("42k");
    }

    @Test
    void formatNumberKeepsRawValueUnderThousand() {
        assertThat(EmailFormatting.formatNumber(999)).isEqualTo("999");
    }

    @Test
    void formatEngagementReturnsEmptyForZeroOrMissing() {
        assertThat(EmailFormatting.formatEngagement(0, "Twitter/X")).isEmpty();
        assertThat(EmailFormatting.formatEngagement(null, "Twitter/X")).isEmpty();
        assertThat(EmailFormatting.formatEngagement(5, null)).isEmpty();
    }

    @Test
    void formatEngagementAppendsSourceSpecificLabel() {
        assertThat(EmailFormatting.formatEngagement(1200, "Twitter/X")).endsWith("&#10084;");
        assertThat(EmailFormatting.formatEngagement(50, "Hacker News")).endsWith("pkt");
        assertThat(EmailFormatting.formatEngagement(50, "GitHub")).endsWith("&#9733;");
        assertThat(EmailFormatting.formatEngagement(50, "Reddit/r/java")).endsWith("&#8593;");
        assertThat(EmailFormatting.formatEngagement(50, "RSS/InfoQ")).isEqualTo("50 ");
    }
}
