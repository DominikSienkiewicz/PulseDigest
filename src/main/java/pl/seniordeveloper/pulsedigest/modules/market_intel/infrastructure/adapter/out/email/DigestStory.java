package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.Signal;

import java.util.Comparator;
import java.util.List;

/**
 * One story as the mail renders it: the item that leads it, every item that carried it, and the
 * lead's signal.
 *
 * <p>A story is the unit the reader cares about. Three items about the same tool registry are one
 * piece of news, and printing them as three entries — in three different sections — reads as a bug
 * however correct each entry is on its own.
 *
 * <p>Collapsing them stays lossless because the members are kept, not discarded: the entry carries a
 * "Potwierdzone w: …" line linking each corroborating source to its own coverage. The reader sees
 * the story once and can still reach every source that carried it.
 *
 * @param lead    the item that represents this story: its highest-scoring member
 * @param members every item that carried it, lead included, in the order the model emitted them
 * @param signal  the lead's deterministic signal, or {@code null} for an unscored edition
 */
record DigestStory(DigestItem lead, List<DigestItem> members, Signal signal, boolean affectsStack) {

    DigestStory {
        members = List.copyOf(members);
    }

    /** Distinct sources that carried this story, sorted — the order the confirmation line prints. */
    List<String> sources() {
        return members.stream()
                .map(DigestItem::source)
                .filter(source -> source != null && !source.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * One item per distinct source, sorted by source — what the confirmation line links to. Where a
     * source carried the story twice, the higher-scoring of its items wins the link.
     */
    List<DigestItem> corroborations() {
        return sources().stream()
                .map(source -> members.stream()
                        .filter(item -> source.equals(item.source()))
                        .max(Comparator.comparingInt(DigestItem::score))
                        .orElseThrow())
                .toList();
    }

    /** Whether more than one source carried this story, and the mail therefore owes a confirmation line. */
    boolean isConfirmed() {
        return sources().size() > 1;
    }

    int score() {
        return lead.score();
    }
}
