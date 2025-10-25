package com.dcruver.orgroam.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Spring AI ChatModel and EmbeddingModel beans.
 *
 * Creates the ChatModel and EmbeddingModel beans that our services need,
 * using Ollama API. This works alongside Embabel's @EnableAgents configuration.
 */
@Configuration
@Slf4j
public class SpringAIConfiguration {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:gpt-oss:20b}")
    private String chatModelName;

    @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text:latest}")
    private String embeddingModelName;

    @Value("${spring.ai.ollama.chat.options.temperature:0.7}")
    private Double temperature;

    /**
     * Create the Ollama API client
     * Note: Spring AI's OllamaApi uses RestClient internally which has default timeouts.
     * For longer timeouts on remote GPU, consider configuring via application.yml:
     * spring.ai.ollama.connection-timeout and spring.ai.ollama.read-timeout
     */
    @Bean
    public OllamaApi ollamaApi() {
        log.info("Creating OllamaApi with base URL: {}", ollamaBaseUrl);
        return OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    /**
     * Create the ChatModel bean for Spring AI integration
     */
    @Bean
    @Primary
    public ChatModel chatModel(
            OllamaApi ollamaApi,
            ToolCallingManager toolCallingManager,
            ObservationRegistry observationRegistry) {
        log.info("Creating ChatModel with Ollama model: {}", chatModelName);

        var options = OllamaOptions.builder()
                .model(chatModelName)
                .temperature(temperature)
                .build();

        // Model management - don't auto-pull models (they should already exist in Ollama)
        var managementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)
                .build();

        return new OllamaChatModel(ollamaApi, options, toolCallingManager,
                observationRegistry, managementOptions);
    }

    /**
     * Create the EmbeddingModel bean for Spring AI integration
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            OllamaApi ollamaApi,
            ObservationRegistry observationRegistry) {
        log.info("Creating EmbeddingModel with Ollama model: {}", embeddingModelName);

        var options = OllamaOptions.builder()
                .model(embeddingModelName)
                .build();

        // Model management - don't auto-pull models (they should already exist in Ollama)
        var managementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)
                .build();

        return new OllamaEmbeddingModel(ollamaApi, options, observationRegistry, managementOptions);
    }
}
