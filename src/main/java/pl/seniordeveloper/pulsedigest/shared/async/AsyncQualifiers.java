package pl.seniordeveloper.pulsedigest.shared.async;

/**
 * Bean name for the data-fetch executor defined in {@code AsyncConfig}.
 *
 * <p>Lives outside {@code shared/infrastructure} so that application services may reference the
 * qualifier string (e.g. on {@code @Qualifier}) without importing infrastructure-configuration types.
 */
public final class AsyncQualifiers {

    public static final String DATA_FETCH_EXECUTOR = "dataFetchExecutor";

    private AsyncQualifiers() {
    }
}
