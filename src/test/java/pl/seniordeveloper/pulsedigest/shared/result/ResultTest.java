package pl.seniordeveloper.pulsedigest.shared.result;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.application.error.MarketIntelError;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    // Any MarketIntelError variant serves as the sample error here; this test is about Result's
    // own behaviour, not about the module's failure vocabulary. It referenced ReportNotAvailable
    // until 2026-10-18, when that variant was removed with the query services nothing called.
    private static final MarketIntelError ERROR = new MarketIntelError.JobVanished("job-1");

    @Test
    void createsSuccessAndFailureFromNullableAndOptional() {
        assertThat(Result.fromNullable("value", ERROR).getValue()).isEqualTo("value");
        assertThat(Result.fromNullable(null, ERROR).getError()).isSameAs(ERROR);
        assertThat(Result.fromOptional(Optional.of("value"), ERROR).getValue()).isEqualTo("value");
        assertThat(Result.fromOptional(Optional.empty(), ERROR).getError()).isSameAs(ERROR);
    }

    @Test
    void mapsAndFlatMapsOnlySuccessValues() {
        Result<String, MarketIntelError> success = Result.success("value");
        Result<String, MarketIntelError> failure = Result.failure(ERROR);

        assertThat(success.map(String::toUpperCase).getValue()).isEqualTo("VALUE");
        assertThat(success.flatMap(v -> Result.success(v + "!")).getValue()).isEqualTo("value!");
        assertThat(failure.map(String::toUpperCase).getError()).isSameAs(ERROR);
        assertThat(failure.flatMap(v -> Result.success(v + "!")).getError()).isSameAs(ERROR);
    }

    @Test
    void exposesOptionalDefaultThrowingAndFoldHelpers() {
        Result<String, MarketIntelError> success = Result.success("value");
        Result<String, MarketIntelError> failure = Result.failure(ERROR);

        assertThat(success.toOptional()).contains("value");
        assertThat(failure.toOptional()).isEmpty();
        assertThat(success.errorToOptional()).isEmpty();
        assertThat(failure.errorToOptional()).contains(ERROR);
        assertThat(failure.orElse("fallback")).isEqualTo("fallback");
        assertThat(failure.orElseGet(() -> "computed")).isEqualTo("computed");
        assertThat(success.orElseThrow(error -> new IllegalStateException(error.message()))).isEqualTo("value");
        assertThat(success.fold(v -> v + "!", MarketIntelError::message)).isEqualTo("value!");
        assertThat(failure.fold(v -> v + "!", MarketIntelError::message))
                .isEqualTo("Digest run job-1 left no job in the tracker");
    }

    @Test
    void peekMethodsRunOnlyForMatchingSide() {
        AtomicReference<String> value = new AtomicReference<>();
        AtomicReference<MarketIntelError> error = new AtomicReference<>();

        Result.success("value")
                .peek(v -> value.set((String) v))
                .peekError(e -> error.set((MarketIntelError) e));
        Result.failure(ERROR)
                .peek(v -> value.set("wrong"))
                .peekError(e -> error.set((MarketIntelError) e));

        assertThat(value).hasValue("value");
        assertThat(error).hasValue(ERROR);
    }

    @Test
    void gettersThrowWhenAccessingWrongSide() {
        assertThatThrownBy(() -> Result.failure(ERROR).getValue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot get value");
        assertThatThrownBy(() -> Result.success("value").getError())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot get error");
        assertThatThrownBy(() -> Result.<String, MarketIntelError>failure(ERROR)
                .orElseThrow(error -> new IllegalArgumentException(error.message())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Digest run job-1 left no job in the tracker");
    }
}
