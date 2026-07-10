package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

/**
 * Thrown when the LLM provider rejected the call because the account is out of quota, credits or
 * rate-limit budget. Retrying or falling back to another model of the same account would fail
 * identically, so this failure is propagated immediately.
 */
public class LlmQuotaException extends LlmSynthesisException {

    public LlmQuotaException(String message, Throwable cause) {
        super(message, cause);
    }
}
