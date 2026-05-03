package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

/**
 * Thrown by LLM infrastructure adapters when synthesis cannot be completed.
 * Caught and recorded as job error at the application layer.
 */
public class LlmSynthesisException extends RuntimeException {

    public LlmSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmSynthesisException(String message) {
        super(message);
    }
}
