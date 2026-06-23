package pl.seniordeveloper.pulsedigest.shared.result;

import pl.seniordeveloper.pulsedigest.shared.error.DomainError;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Type-safe result pattern for explicit error handling without exceptions.
 *
 * @param <T> the success value type
 * @param <E> the error type
 */
public sealed interface Result<T, E extends DomainError> permits Result.Success, Result.Failure {

    static <T, E extends DomainError> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E extends DomainError> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    /**
     * Creates a result from a nullable value. Returns failure with the provided error if the value is
     * null.
     */
    static <T, E extends DomainError> Result<T, E> fromNullable(T value, E errorIfNull) {
        return value != null ? success(value) : failure(errorIfNull);
    }

    /**
     * Creates a result from an Optional. Returns failure with the provided error if the optional is
     * empty.
     */
    static <T, E extends DomainError> Result<T, E> fromOptional(
            Optional<T> optional, E errorIfEmpty) {
        return optional.map(Result::<T, E>success).orElseGet(() -> failure(errorIfEmpty));
    }

    default <R> Result<R, E> map(Function<T, R> mapper) {
        return switch (this) {
            case Success(var value) -> Result.success(mapper.apply(value));
            case Failure(var error) -> Result.failure(error);
        };
    }

    default <R> Result<R, E> flatMap(Function<T, Result<R, E>> mapper) {
        return switch (this) {
            case Success(var value) -> mapper.apply(value);
            case Failure(var error) -> Result.failure(error);
        };
    }

    /**
     * Returns the success value if present, otherwise throws.
     */
    default T getValue() {
        return switch (this) {
            case Success(var value) -> value;
            case Failure(var error) -> throw new IllegalStateException("Cannot get value from Failure result: " + error);
        };
    }

    /**
     * Returns the error if present, otherwise throws.
     */
    default E getError() {
        return switch (this) {
            case Success(_) -> throw new IllegalStateException("Cannot get error from Success result");
            case Failure(var error) -> error;
        };
    }

    /**
     * Returns the success value as an Optional.
     */
    default Optional<T> toOptional() {
        return switch (this) {
            case Success(var value) -> Optional.ofNullable(value);
            case Failure(_) -> Optional.empty();
        };
    }

    /**
     * Returns the error as an Optional.
     */
    default Optional<E> errorToOptional() {
        return switch (this) {
            case Success(_) -> Optional.empty();
            case Failure(var error) -> Optional.of(error);
        };
    }

    /**
     * Returns the success value or the provided default.
     */
    default T orElse(T defaultValue) {
        return switch (this) {
            case Success(var value) -> value;
            case Failure(_) -> defaultValue;
        };
    }

    /**
     * Returns the success value or computes it using the provided supplier.
     */
    default T orElseGet(Supplier<T> supplier) {
        return switch (this) {
            case Success(var value) -> value;
            case Failure(_) -> supplier.get();
        };
    }

    /**
     * Returns the success value or throws an exception computed from the error.
     */
    default <X extends Throwable> T orElseThrow(Function<E, X> exceptionMapper) throws X {
        return switch (this) {
            case Success(var value) -> value;
            case Failure(var error) -> throw exceptionMapper.apply(error);
        };
    }

    /**
     * Executes the given consumer if this is a success.
     */
    default Result<T, E> peek(Consumer<T> consumer) {
        if (this instanceof Success(var value)) {
            consumer.accept(value);
        }
        return this;
    }

    /**
     * Executes the given consumer if this is a failure.
     */
    default Result<T, E> peekError(Consumer<E> consumer) {
        if (this instanceof Failure(var error)) {
            consumer.accept(error);
        }
        return this;
    }

    /**
     * Folds this result into a single value using the provided functions.
     */
    default <U> U fold(Function<T, U> onSuccess, Function<E, U> onFailure) {
        return switch (this) {
            case Success(var value) -> onSuccess.apply(value);
            case Failure(var error) -> onFailure.apply(error);
        };
    }

    record Success<T, E extends DomainError>(T value) implements Result<T, E> {
    }

    record Failure<T, E extends DomainError>(E error) implements Result<T, E> {
    }
}
