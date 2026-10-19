package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PromptCostEstimate;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out.PromptCostEstimatePort;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.LlmProperties;

/**
 * Sizes the real synthesis prompt and prices it from the configured rate card.
 *
 * <p>The token figure is characters divided by four — the usual English approximation, and stated as
 * an approximation everywhere it is shown. It is built from the prompt the production builder would
 * actually send, system message included, so it tracks prompt edits instead of drifting away from
 * them. Nothing here calls the model.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OpenAiPromptCostEstimator implements PromptCostEstimatePort {

    private static final int CHARS_PER_TOKEN = 4;

    private final ReportPromptBuilder promptBuilder;
    private final LlmProperties llm;

    @Override
    public PromptCostEstimate estimate(ResearchResult research) {
        if (research == null) {
            return PromptCostEstimate.unpriced(0);
        }
        int characters = promptBuilder.buildSystemPrompt().length()
                + promptBuilder.buildUserPrompt(research).length();
        long tokens = (long) Math.ceil((double) characters / CHARS_PER_TOKEN);

        LlmProperties.Price price = llm.priceOf(llm.synthesis().model());
        if (!price.isKnown()) {
            return PromptCostEstimate.unpriced(tokens);
        }
        // Completion tokens are unknowable before the call; the ceiling is the honest upper bound.
        double cost = price.costOf(tokens, llm.synthesis().maxTokens());
        return new PromptCostEstimate(tokens, cost, true);
    }
}
