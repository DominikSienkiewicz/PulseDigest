package pl.seniordeveloper.pulsedigest.shared.async;

/**
 * Bean names for the async executors defined in {@code AsyncConfig}.
 *
 * <p>Lives outside {@code shared/infrastructure} so that application services may reference
 * the qualifier string (e.g. on {@code @Async}, {@code @Qualifier}) without importing
 * infrastructure-configuration types.
 */
public final class AsyncQualifiers {

    public static final String REPORT_EXECUTOR = "reportTaskExecutor";
    public static final String DATA_FETCH_EXECUTOR = "dataFetchExecutor";

    private AsyncQualifiers() {
    }
}
