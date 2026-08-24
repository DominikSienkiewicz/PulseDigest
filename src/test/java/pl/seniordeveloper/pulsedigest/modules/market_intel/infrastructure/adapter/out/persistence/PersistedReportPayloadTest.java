package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.PersistedReport;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PersistedReportPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void noDerivedAccessorLeaksIntoTheStoredPayload() {
        List<String> leaks = persistedRecordGraph().stream()
                .flatMap(type -> derivedAccessorsIn(type).stream())
                .toList();

        assertThat(leaks)
                .as("Jackson serializes public getX()/isX() methods into the JSONB payload, but a record "
                        + "has no component to read them back into — annotate each one with @JsonIgnore")
                .isEmpty();
    }

    @Test
    void aFullyPopulatedReportSurvivesAJsonRoundTrip() throws Exception {
        PersistedReport report = Instancio.create(PersistedReport.class);

        PersistedReport restored =
                objectMapper.readValue(objectMapper.writeValueAsString(report), PersistedReport.class);

        assertThat(restored.jobId()).isEqualTo(report.jobId());
        assertThat(restored.fetchReports()).hasSameSizeAs(report.fetchReports());
        assertThat(restored.report().signals()).hasSameSizeAs(report.report().signals());
    }

    private static Set<Class<?>> persistedRecordGraph() {
        Set<Class<?>> records = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(PersistedReport.class);
        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (!type.isRecord() || !records.add(type)) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                collectReferencedTypes(component.getGenericType(), pending);
            }
        }
        return records;
    }

    private static void collectReferencedTypes(Type type, Deque<Class<?>> pending) {
        if (type instanceof Class<?> clazz) {
            pending.add(clazz);
        } else if (type instanceof ParameterizedType parameterized) {
            collectReferencedTypes(parameterized.getRawType(), pending);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectReferencedTypes(argument, pending);
            }
        }
    }

    private static List<String> derivedAccessorsIn(Class<?> type) {
        Set<String> componentNames = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.getName().matches("^(get|is)[A-Z].*"))
                .filter(method -> !componentNames.contains(method.getName()))
                .filter(method -> !method.isAnnotationPresent(JsonIgnore.class))
                .map(Method::getName)
                .map(name -> type.getSimpleName() + "#" + name + "()")
                .toList();
    }
}
