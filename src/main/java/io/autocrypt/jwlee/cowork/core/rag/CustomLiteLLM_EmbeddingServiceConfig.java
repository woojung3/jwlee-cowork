package io.autocrypt.jwlee.cowork.core.rag;

import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.SpringAiEmbeddingService;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("vertex")
public class CustomLiteLLM_EmbeddingServiceConfig {

    @Bean
    public EmbeddingService embedding(
            @Value("${LITELLM_BASE_URL:https://aigw.autocrypt.co.kr}") String baseUrl,
            @Value("${LITELLM_API_KEY:${LITELLM_MASTER_KEY:}}") String apiKey) {

        var openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(new SimpleApiKey(apiKey))
                .restClientBuilder(RestClient.builder())
                .build();

        EmbeddingModel openAiEmbeddingModel = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model("embedding").build());

        return new SpringAiEmbeddingService(
                "embedding", 
                "LiteLLM-Custom",
                openAiEmbeddingModel,
                null,
                null);
    }
}
