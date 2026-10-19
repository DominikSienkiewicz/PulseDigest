package pl.seniordeveloper.pulsedigest.modules.market_intel.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy.SavedBacklogPolicy;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PastEdition;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedBacklog;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.SavedItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns saved feedback rows back into something the reader can recognise (N05 follow-up).
 *
 * <p>Until this existed, "zapisz" was write-only: the click was recorded, counted as zero, and read
 * by nothing. A button whose effect never surfaces is a button nobody presses twice.
 *
 * <p>Two things the data cannot support, and which this therefore does not claim. It cannot know
 * whether the reader <em>read</em> the item — the batch serves no HTTP and nothing observes a click
 * on the saved link — so the shelf is "saved and not voted on since", never "unread". And it cannot
 * name an item on its own: the receiver stores a URL, so the headline comes from the stored edition
 * the item was saved out of, and an item whose edition has aged out of the history window is shown
 * by its URL rather than dropped.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SavedBacklogService {

    private final SavedBacklogPolicy policy;

    /**
     * The shelf as this edition should report it: oldest first, titled where history can name them,
     * and capped — with the true total kept alongside, since a reader who later discovers the
     * section was hiding items stops believing the ones it shows.
     */
    public SavedBacklog assemble(List<SavedItem> saved, List<PastEdition> history) {
        if (!policy.enabled() || saved == null || saved.isEmpty()) {
            return SavedBacklog.empty();
        }
        Map<String, String> titlesByUrl = titlesByUrl(history);
        List<SavedItem> named = saved.stream()
                .map(item -> item.withTitle(titlesByUrl.get(item.url())))
                .toList();
        List<SavedItem> shown = named.stream().limit(Math.max(0, policy.maxItems())).toList();

        log.info("saved-backlog total={} shown={} named={}",
                named.size(), shown.size(), named.stream().filter(item -> item.title() != null).count());
        return new SavedBacklog(shown, named.size());
    }

    /**
     * Headlines from stored editions, newest edition winning. The same URL can appear in more than
     * one past edition; the most recent rendering is the wording the reader saw last.
     */
    private static Map<String, String> titlesByUrl(List<PastEdition> history) {
        Map<String, String> titles = new HashMap<>();
        if (history == null) {
            return titles;
        }
        history.stream()
                .sorted((a, b) -> a.generatedAt().compareTo(b.generatedAt()))
                .flatMap(edition -> edition.signals().stream())
                .map(signal -> signal.item())
                .filter(item -> item != null && item.url() != null && item.title() != null)
                .forEach(item -> titles.put(item.url(), item.title()));
        return titles;
    }
}
