package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ArxivProperties;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static org.assertj.core.api.Assertions.assertThat;

class ArxivSearchAdapterIT {

    private static final String ATOM_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>http://arxiv.org/abs/2504.12345v1</id>
                <title>LLM Agent Systems for Java</title>
                <summary>Novel llm-based agent framework built on JVM with rag retrieval.</summary>
                <author><name>Test Author</name></author>
                <category term="cs.AI" scheme="http://arxiv.org/schemas/atom"/>
                <published>2099-06-01T10:00:00Z</published>
                <updated>2099-06-01T10:00:00Z</updated>
              </entry>
            </feed>
            """;

    private WireMockServer wireMock;
    private ArxivSearchAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        ArxivProperties props =
                new ArxivProperties("cs.AI,cs.LG", "agent,llm,rag", 10, 24);
        adapter = new ArxivSearchAdapter(props);

        RestClient testClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("User-Agent", "PulseDigest/1.0 arxiv-reader")
                .build();

        Field restClientField = ArxivSearchAdapter.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(adapter, testClient);
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void fetchesAndReturnsParsedPapers() {
        wireMock.stubFor(get(urlPathEqualTo("/api/query"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/atom+xml; charset=UTF-8")
                        .withBody(ATOM_RESPONSE)));

        List<ResearchPaper> papers = adapter.fetchLatestPapers();

        assertThat(papers).hasSize(1);
        assertThat(papers.get(0).arxivId()).isEqualTo("2504.12345");
        assertThat(papers.get(0).title()).isEqualTo("LLM Agent Systems for Java");
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("/api/query")))).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenApiFails() {
        wireMock.stubFor(get(urlPathEqualTo("/api/query"))
                .willReturn(aResponse().withStatus(503)));

        List<ResearchPaper> papers = adapter.fetchLatestPapers();

        assertThat(papers).isEmpty();
    }
}
