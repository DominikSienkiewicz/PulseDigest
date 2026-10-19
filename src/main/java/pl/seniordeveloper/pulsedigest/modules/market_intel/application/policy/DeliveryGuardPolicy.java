package pl.seniordeveloper.pulsedigest.modules.market_intel.application.policy;

/**
 * Whether the one-edition-per-day interlock is enforced (F16).
 *
 * <p>A separate switch from the timeout, which belongs to the adapter that owns the SQL: the
 * processor needs to know only whether to ask.
 *
 * @param enabled whether a run checks and claims before sending
 */
public record DeliveryGuardPolicy(boolean enabled) {
}
