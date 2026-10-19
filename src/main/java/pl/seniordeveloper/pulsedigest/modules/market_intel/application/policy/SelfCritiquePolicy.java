package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * Whether the self-critique runs, and whether it is allowed to act (N23).
 *
 * <p>Two flags rather than one, because the catalogue asks for exactly this sequence: run it in
 * shadow first, log what it <em>would</em> have cut, read that for a few editions, and only then
 * give it authority. A second model opinion can be confidently wrong, and an edition stripped of
 * good items by an unverified critic is a worse failure than a boring edition.
 *
 * @param enabled   whether the critique call is made at all
 * @param enforcing whether its verdict removes items, or is only recorded
 */
public record SelfCritiquePolicy(boolean enabled, boolean enforcing) {
}
