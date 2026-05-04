package pl.seniordeveloper.pulsedigest.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlCanonicalizerTest {

    @Test
    void stripsAllUtmParams() {
        String url = "https://www.infoq.com/article?utm_campaign=infoq_content&utm_source=infoq";

        assertThat(UrlCanonicalizer.canonicalize(url))
                .isEqualTo("https://www.infoq.com/article");
    }

    @Test
    void stripsRealWorldInfoQUrl() {
        String url = "https://www.infoq.com/news/2026/05/cloudflare-security-dashboard/"
                + "?utm_campaign=infoq_content&utm_source=infoq&utm_medium=feed&utm_term=global";

        assertThat(UrlCanonicalizer.canonicalize(url))
                .isEqualTo("https://www.infoq.com/news/2026/05/cloudflare-security-dashboard/");
    }

    @Test
    void stripsFbclid() {
        assertThat(UrlCanonicalizer.canonicalize("https://example.com/x?fbclid=ABC123"))
                .isEqualTo("https://example.com/x");
    }

    @Test
    void stripsTwitterShareParam() {
        assertThat(UrlCanonicalizer.canonicalize("https://x.com/user/status/123?s=20"))
                .isEqualTo("https://x.com/user/status/123");
    }

    @Test
    void preservesGithubRefParam() {
        // ref jest semantyczny na GitHubie — strip == zepsuty link
        assertThat(UrlCanonicalizer.canonicalize(
                "https://github.com/repo/issues/42?ref=main&utm_source=spam"))
                .isEqualTo("https://github.com/repo/issues/42?ref=main");
    }

    @Test
    void preservesNonTrackingParamsInMixedQuery() {
        assertThat(UrlCanonicalizer.canonicalize(
                "https://example.com/?id=42&utm_source=x&q=foo"))
                .isEqualTo("https://example.com/?id=42&q=foo");
    }

    @Test
    void preservesFragment() {
        assertThat(UrlCanonicalizer.canonicalize("https://example.com/?utm_source=x#section"))
                .isEqualTo("https://example.com/#section");
    }

    @Test
    void returnsUrlUnchangedWhenNoQuery() {
        assertThat(UrlCanonicalizer.canonicalize("https://example.com/path"))
                .isEqualTo("https://example.com/path");
    }

    @Test
    void returnsAsIsForNullOrBlank() {
        assertThat(UrlCanonicalizer.canonicalize(null)).isNull();
        assertThat(UrlCanonicalizer.canonicalize("")).isEmpty();
        assertThat(UrlCanonicalizer.canonicalize("   ")).isEqualTo("   ");
    }

    @Test
    void returnsAsIsForMalformedUrl() {
        assertThat(UrlCanonicalizer.canonicalize("not a url"))
                .isEqualTo("not a url");
    }

    @Test
    void stripsTrackingOnlyQueryLeavingNoTrailingQuestionMark() {
        assertThat(UrlCanonicalizer.canonicalize("https://example.com/?utm_source=x"))
                .isEqualTo("https://example.com/");
    }

    @Test
    void stripsAllListedExactTrackingParams() {
        String url = "https://example.com/?gclid=A&gbraid=B&wbraid=C&mc_cid=D&mc_eid=E"
                + "&igshid=F&_ga=G&_gl=H&ref_src=I&ref_url=J&keep=yes";

        assertThat(UrlCanonicalizer.canonicalize(url))
                .isEqualTo("https://example.com/?keep=yes");
    }

    @Test
    void preservesParamOrder() {
        assertThat(UrlCanonicalizer.canonicalize(
                "https://example.com/?a=1&utm_source=x&b=2&utm_medium=y&c=3"))
                .isEqualTo("https://example.com/?a=1&b=2&c=3");
    }
}
