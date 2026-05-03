package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Research paper fetched from arXiv.
 */
public record ResearchPaper(
        String arxivId,
        String title,
        String abstractText,
        List<String> authors,
        String url,
        String primaryCategory,
        LocalDateTime publishedAt
) {}
