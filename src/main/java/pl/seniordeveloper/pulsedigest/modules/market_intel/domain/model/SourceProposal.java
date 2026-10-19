package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A source the evidence suggests is worth adding (N28).
 *
 * <p>It inverts ADR-0002's friction argument without removing the friction: instead of a deliberate
 * edit being the only thing standing between the digest and a bad source, evidence proposes good
 * ones and a human still makes the edit. Nothing here changes configuration — a proposal that could
 * add its own source would be exactly the automation the friction exists to prevent.
 *
 * @param name     the feed, account or site proposed
 * @param kind     what it is — an RSS feed, an account, a release page
 * @param why      the case for it, in the model's own words
 * @param evidence URLs of already-published items that justify it; a proposal without these is
 *                 a suggestion, and suggestions without evidence are spam
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceProposal(String name, String kind, String why, List<String> evidence) {

    public SourceProposal {
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
    }

    /** A proposal nobody can check is not worth printing. */
    public boolean isSupported() {
        return name != null && !name.isBlank() && !evidence.isEmpty();
    }
}
