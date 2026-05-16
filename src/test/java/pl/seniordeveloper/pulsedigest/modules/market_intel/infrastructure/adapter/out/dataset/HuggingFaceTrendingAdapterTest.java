package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.HuggingFaceModel;
import pl.seniordeveloper.pulsedigest.shared.infrastructure.config.HuggingFaceProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HuggingFaceTrendingAdapterTest {

    private static final String MODELS_JSON = """
            [
              {
                "modelId": "meta-llama/Llama-3-8B",
                "pipeline_tag": "text-generation",
                "downloads": 50000,
                "likes": 800,
                "lastModified": "2099-05-01T08:00:00.000Z"
              },
              {
                "modelId": "openai-whisper/large-v3",
                "pipeline_tag": "automatic-speech-recognition",
                "downloads": 12000,
                "likes": 50,
                "lastModified": "2099-04-30T08:00:00.000Z"
              },
              {
                "modelId": "tiny-author/obscure-model",
                "pipeline_tag": "text-generation",
                "downloads": 3,
                "likes": 1,
                "lastModified": "2099-04-29T08:00:00.000Z"
              },
              {
                "modelId": "irrelevant/sentiment-analyzer",
                "pipeline_tag": "text-classification",
                "downloads": 100000,
                "likes": 2000,
                "lastModified": "2099-04-28T08:00:00.000Z"
              }
            ]
            """;

    private HuggingFaceTrendingAdapter adapter;

    @BeforeEach
    void setUp() {
        HuggingFaceProperties props =
                new HuggingFaceProperties(
                        "https://huggingface.co/api/models",
                        30,
                        10,
                        1000,
                        List.of("text-generation", "automatic-speech-recognition")
                );
        adapter = new HuggingFaceTrendingAdapter(props, new ObjectMapper());
    }

    @Test
    void keepsModelsAboveEngagementThreshold() {
        List<HuggingFaceModel> models = adapter.parseModels(MODELS_JSON);

        assertThat(models).hasSize(2);
        assertThat(models.get(0).id()).isEqualTo("meta-llama/Llama-3-8B");
        assertThat(models.get(0).author()).isEqualTo("meta-llama");
        assertThat(models.get(0).pipelineTag()).isEqualTo("text-generation");
        assertThat(models.get(0).url()).isEqualTo("https://huggingface.co/meta-llama/Llama-3-8B");
    }

    @Test
    void filtersOutLowEngagementModelsBelowBothLikesAndDownloads() {
        List<HuggingFaceModel> models = adapter.parseModels(MODELS_JSON);

        assertThat(models).extracting(HuggingFaceModel::id)
                .doesNotContain("tiny-author/obscure-model");
    }

    @Test
    void filtersOutModelsWithNonRelevantPipelineTag() {
        List<HuggingFaceModel> models = adapter.parseModels(MODELS_JSON);

        assertThat(models).extracting(HuggingFaceModel::id)
                .doesNotContain("irrelevant/sentiment-analyzer");
    }

    @Test
    void returnsEmptyListForMalformedJson() {
        List<HuggingFaceModel> models = adapter.parseModels("NOT JSON");
        assertThat(models).isEmpty();
    }

    @Test
    void keepsModelWhenLikesBelowThresholdButDownloadsAboveThreshold() {
        String json = """
                [{"modelId":"foo/bar","pipeline_tag":"text-generation",
                  "downloads":5000,"likes":2,
                  "lastModified":"2099-01-01T00:00:00.000Z"}]
                """;
        List<HuggingFaceModel> models = adapter.parseModels(json);

        assertThat(models).hasSize(1);
    }
}
