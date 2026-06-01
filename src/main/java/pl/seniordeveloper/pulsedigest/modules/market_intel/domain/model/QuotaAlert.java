package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

import java.util.List;

/**
 * Payload for the standalone "quota exhausted" alert email — sent when the digest could NOT be
 * produced at all (e.g. the LLM provider ran out of credits, or every data source was rate-limited),
 * so the recipient still learns by email which account needs topping up.
 *
 * @param exhaustedAccounts human-readable names of the accounts/APIs to top up (never empty)
 * @param detail            optional technical detail (root error message) for context; may be blank
 */
public record QuotaAlert(List<String> exhaustedAccounts, String detail) {

    public QuotaAlert {
        exhaustedAccounts = exhaustedAccounts != null ? List.copyOf(exhaustedAccounts) : List.of();
    }

    public boolean isEmpty() {
        return exhaustedAccounts.isEmpty();
    }
}
