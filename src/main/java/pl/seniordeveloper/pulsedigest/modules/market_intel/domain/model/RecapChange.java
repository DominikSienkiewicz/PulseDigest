package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model;

/**
 * What happened to a story over the course of one week of editions.
 *
 * <p>{@code FADED} is deliberately part of the vocabulary: a digest that only ever reports things
 * going up is a hype machine. Saying "Monday's 🔴 came to nothing" is what makes the 🔴 credible.
 */
public enum RecapChange {
    ESCALATED, CONFIRMED, FADED
}
