package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * Entry from the Thoughtworks Technology Radar, published roughly twice a year.
 *
 * <p>No timestamp, deliberately (N57). The source JSON carries no dates — only an {@code isNew}
 * flag — and this record used to be stamped {@code LocalDateTime.now()} at parse time, which made
 * every blip of a six-month-old volume look minted this morning. The field was then read in exactly
 * one place: a copy constructor that copied it forward. Nothing consumed it, nothing filtered on it,
 * and the prompt never saw it. Removing the field removes the lie, which is better than finding a
 * more defensible lie to tell in its place.
 */
public record RadarEntry(
        String name,
        String ring,
        String quadrant,
        String description,
        String url
) {}
