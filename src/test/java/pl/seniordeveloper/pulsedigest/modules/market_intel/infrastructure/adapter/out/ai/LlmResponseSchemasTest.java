package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.ai;

import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.DigestItem;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** N22. The schema is derived from the record, and this is what keeps it that way. */
class LlmResponseSchemasTest {

    @Test
    @SuppressWarnings("unchecked")
    void theItemSchemaNamesExactlyWhatDigestItemDeclares() {
        // The reason the schema is generated rather than written out: a renamed component has to
        // change both, and a hand-written copy only ever changes when somebody remembers.
        Map<String, Object> schema = LlmResponseSchemas.synthesisSchema();
        Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) schema.get("properties")).get("items")).get("items");
        List<String> declared = List.of(DigestItem.class.getRecordComponents()).stream()
                .map(LlmResponseSchemasTest::jsonName)
                .toList();

        assertThat(((Map<String, Object>) items.get("properties")).keySet())
                .containsExactlyElementsOf(declared);
    }

    @Test
    @SuppressWarnings("unchecked")
    void strictModeRequiresEveryPropertyAndForbidsExtras() {
        // Both are provider rules, and getting either wrong is rejected at request time rather than
        // at parse time — which is a worse place to find out.
        Map<String, Object> schema = LlmResponseSchemas.synthesisSchema();

        assertThat(schema).containsEntry("additionalProperties", false);
        assertThat((List<String>) schema.get("required"))
                .containsExactlyInAnyOrderElementsOf(
                        ((Map<String, Object>) schema.get("properties")).keySet());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anOptionalFieldIsNullableRatherThanAbsent() {
        // Strict mode has no optional properties, so "the model had nothing to say" has to be
        // expressible as a value. engagement_score is the case that forces it.
        Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) LlmResponseSchemas.synthesisSchema().get("properties"))
                        .get("items")).get("items");
        Map<String, Object> properties = (Map<String, Object>) items.get("properties");

        assertThat((Map<String, Object>) properties.get("engagement_score"))
                .containsEntry("type", List.of("integer", "null"));
        assertThat((Map<String, Object>) properties.get("score"))
                .as("a primitive int is never null")
                .containsEntry("type", "integer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theResponseFormatBlockIsWhatTheProviderExpects() {
        Map<String, Object> format = LlmResponseSchemas.responseFormat(
                "pulsedigest_edition", LlmResponseSchemas.synthesisSchema());

        assertThat(format).containsEntry("type", "json_schema");
        Map<String, Object> named = (Map<String, Object>) format.get("json_schema");
        assertThat(named).containsEntry("name", "pulsedigest_edition").containsEntry("strict", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTriageSchemaIsTwoIntegersPerEntry() {
        Map<String, Object> scores = (Map<String, Object>) ((Map<String, Object>)
                LlmResponseSchemas.preScoringSchema().get("properties")).get("scores");
        Map<String, Object> entry = (Map<String, Object>) scores.get("items");

        assertThat(((Map<String, Object>) entry.get("properties")).keySet()).containsExactly("i", "s");
    }

    private static String jsonName(RecordComponent component) {
        var annotation = component.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
        if (annotation == null) {
            try {
                annotation = component.getDeclaringRecord().getDeclaredField(component.getName())
                        .getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
            } catch (NoSuchFieldException e) {
                annotation = null;
            }
        }
        return annotation != null && !annotation.value().isBlank()
                ? annotation.value() : component.getName();
    }
}
