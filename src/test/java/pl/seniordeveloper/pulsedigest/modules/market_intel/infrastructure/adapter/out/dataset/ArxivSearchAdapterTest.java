package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchPaper;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.ArxivProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArxivSearchAdapterTest {

    private static final String ATOM_FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>http://arxiv.org/abs/2504.12345v1</id>
                <title>LLM-Based Agents for Software Engineering</title>
                <summary>Novel llm agent approach with rag retrieval for software engineering tasks.</summary>
                <author><name>Alice Smith</name></author>
                <author><name>Bob Jones</name></author>
                <category term="cs.AI" scheme="http://arxiv.org/schemas/atom"/>
                <published>2099-01-01T10:00:00Z</published>
                <updated>2099-01-01T10:00:00Z</updated>
              </entry>
              <entry>
                <id>http://arxiv.org/abs/2504.99999v1</id>
                <title>Pure Topology in Abstract Spaces</title>
                <summary>Abstract about pure mathematics with no relevant keywords for engineers.</summary>
                <author><name>Carol White</name></author>
                <category term="math.GN" scheme="http://arxiv.org/schemas/atom"/>
                <published>2099-01-01T09:00:00Z</published>
                <updated>2099-01-01T09:00:00Z</updated>
              </entry>
            </feed>
            """;

    private static final String EMPTY_FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
            </feed>
            """;

    private ArxivSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        ArxivProperties props =
                new ArxivProperties("cs.AI,cs.LG", "agent,llm,rag", 10, 24);
        adapter = new ArxivSearchAdapter(props);
    }

    @Test
    void parsesMatchingPaperAndFiltersNonMatching() {
        List<ResearchPaper> papers = adapter.parseFeed(ATOM_FEED, 0);

        assertThat(papers).hasSize(1);
        ResearchPaper paper = papers.get(0);
        assertThat(paper.arxivId()).isEqualTo("2504.12345");
        assertThat(paper.title()).isEqualTo("LLM-Based Agents for Software Engineering");
        assertThat(paper.primaryCategory()).isEqualTo("cs.AI");
        assertThat(paper.authors()).containsExactly("Alice Smith", "Bob Jones");
        assertThat(paper.url()).isEqualTo("https://arxiv.org/abs/2504.12345");
        assertThat(paper.abstractText()).contains("rag retrieval");
    }

    @Test
    void returnsEmptyListForEmptyFeed() {
        List<ResearchPaper> papers = adapter.parseFeed(EMPTY_FEED, 0);
        assertThat(papers).isEmpty();
    }

    @Test
    void truncatesAbstractTo500Chars() {
        String longAbstract = "agent " + "x".repeat(600);
        String feedWithLongAbstract = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <id>http://arxiv.org/abs/2504.00001v1</id>
                    <title>Agent Systems</title>
                    <summary>%s</summary>
                    <author><name>Test Author</name></author>
                    <category term="cs.AI" scheme="http://arxiv.org/schemas/atom"/>
                    <published>2099-01-01T10:00:00Z</published>
                    <updated>2099-01-01T10:00:00Z</updated>
                  </entry>
                </feed>
                """.formatted(longAbstract);

        List<ResearchPaper> papers = adapter.parseFeed(feedWithLongAbstract, 0);

        assertThat(papers).hasSize(1);
        assertThat(papers.get(0).abstractText().length()).isLessThanOrEqualTo(500);
    }
}
