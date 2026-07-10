package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

/**
 * Thrown when the model stopped at the token cap ({@code finish_reason=length}) and its JSON output
 * is therefore incomplete. Retrying the identical request is pointless — the caller recovers by
 * re-sending fewer items instead.
 */
public class LlmTruncatedException extends LlmSynthesisException {

    public LlmTruncatedException(String message) {
        super(message);
    }
}
