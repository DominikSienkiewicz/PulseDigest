package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON schemas for the two model calls, derived from the records their responses bind to (N22).
 *
 * <p>The retry ladder, {@code LlmTruncatedException} and the fallback model all exist to survive
 * output that is the wrong shape. A schema-constrained response removes most of that failure class
 * at the source: the provider will not return a document that does not match.
 *
 * <p><strong>Derived, not transcribed.</strong> The item schema is read off {@link DigestItem}'s
 * record components by reflection, so a renamed or added component changes the schema in the same
 * commit. A hand-written copy of a record is a second definition of one thing, and the two diverge
 * the first time somebody is in a hurry.
 *
 * <p>Strict mode has rules worth knowing: every property must appear in {@code required}, and
 * {@code additionalProperties} must be false. Optionality is therefore expressed as a nullable type
 * rather than by omission — which is also closer to the truth, since the model has to decide
 * something for every field either way.
 */
final class LlmResponseSchemas {

    private LlmResponseSchemas() {
    }

    /** The whole edition: the four fields the model produces, and nothing the pipeline derives. */
    static Map<String, Object> synthesisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("email_preview", type("string"));
        properties.put("editorial", type("string"));
        properties.put("top_insights", array(type("string")));
        properties.put("items", array(objectOf(DigestItem.class)));
        return object(properties);
    }

    /** Triage: one score per item index. */
    static Map<String, Object> preScoringSchema() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("i", type("integer"));
        entry.put("s", type("integer"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("scores", array(object(entry)));
        return object(properties);
    }

    /** The {@code response_format} block for a named schema, in strict mode. */
    static Map<String, Object> responseFormat(String name, Map<String, Object> schema) {
        // LinkedHashMap, not Map.of: the request body is asserted on in tests and read by humans in
        // failure output, and an unordered map serialises differently from one run to the next.
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("name", name);
        named.put("strict", true);
        named.put("schema", schema);
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("json_schema", named);
        return format;
    }

    /**
     * A record as an object schema, with each component's JSON name and a type derived from its
     * Java type. Reference types are nullable because the model may genuinely have nothing to say.
     */
    private static Map<String, Object> objectOf(Class<?> record) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (RecordComponent component : record.getRecordComponents()) {
            properties.put(jsonName(component), schemaFor(component.getType()));
        }
        return object(properties);
    }

    /**
     * The name Jackson will use.
     *
     * <p>Read from the backing field as well as the component: {@code @JsonProperty} does not list
     * {@code RECORD_COMPONENT} among its targets, so it lands on the generated field and
     * {@code RecordComponent.getAnnotation} returns nothing for it. Reading only the component
     * silently produced {@code engagementScore} where Jackson expects {@code engagement_score} —
     * a schema that disagrees with the parser it exists to constrain.
     */
    private static String jsonName(RecordComponent component) {
        JsonProperty annotation = component.getAnnotation(JsonProperty.class);
        if (annotation == null) {
            try {
                annotation = component.getDeclaringRecord()
                        .getDeclaredField(component.getName())
                        .getAnnotation(JsonProperty.class);
            } catch (NoSuchFieldException e) {
                annotation = null;
            }
        }
        return annotation != null && !annotation.value().isBlank()
                ? annotation.value()
                : component.getName();
    }

    private static Map<String, Object> schemaFor(Class<?> type) {
        if (type == int.class || type == long.class) {
            return type("integer");
        }
        if (type == Integer.class || type == Long.class) {
            return nullable("integer");
        }
        if (type == boolean.class) {
            return type("boolean");
        }
        if (type == double.class || type == float.class) {
            return type("number");
        }
        return nullable("string");
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", items);
        return schema;
    }

    private static Map<String, Object> type(String jsonType) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", jsonType);
        return schema;
    }

    private static Map<String, Object> nullable(String jsonType) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", List.of(jsonType, "null"));
        return schema;
    }
}
