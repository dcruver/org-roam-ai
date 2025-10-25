package com.dcruver.orgroam.nlp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for LLM operations using Ollama via Spring AI.
 * Embabel creates ChatModel beans automatically from configured models.
 */
@Service
@Slf4j
public class OllamaChatService {

    private final ChatModel chatModel;

    /**
     * Inject the ChatModel bean created by Embabel.
     * Embabel's @EnableAgents(localModels = {LocalModels.OLLAMA}) automatically
     * creates ChatModel beans from available Ollama models.
     */
    public OllamaChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("OllamaChatService initialized with ChatModel: {}", chatModel.getClass().getSimpleName());
    }

    /**
     * Generate a response from a simple prompt
     */
    public String chat(String userMessage) {
        return chat(null, userMessage);
    }

    /**
     * Generate a response with system and user messages
     */
    public String chat(String systemMessage, String userMessage) {
        List<Message> messages = new ArrayList<>();

        if (systemMessage != null && !systemMessage.isBlank()) {
            messages.add(new SystemMessage(systemMessage));
        }

        messages.add(new UserMessage(userMessage));

        try {
            ChatResponse response = chatModel.call(new Prompt(messages));
            if (response.getResults().isEmpty()) {
                log.warn("No response generated");
                return "";
            }

            // getContent() might be deprecated, use getText() or similar
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Failed to generate chat response", e);
            return "";
        }
    }

    /**
     * Generate link suggestions rationale
     */
    public String generateLinkRationale(String sourceNote, String targetNote, double similarity) {
        String systemMessage = "You are an expert at analyzing knowledge base connections. " +
            "Provide a concise rationale (1-2 sentences) for why two notes should be linked.";

        String userMessage = String.format(
            "Source note:\n%s\n\nTarget note:\n%s\n\nSimilarity score: %.3f\n\n" +
            "Why should these notes be linked?",
            truncate(sourceNote, 500),
            truncate(targetNote, 500),
            similarity
        );

        return chat(systemMessage, userMessage);
    }

    /**
     * Analyze potential note split/merge
     */
    public String analyzeSplitMerge(String noteContent, String operation) {
        String systemMessage = "You are an expert at analyzing knowledge organization. " +
            "Analyze whether a note should be split or merged and provide rationale.";

        String userMessage = String.format(
            "Operation: %s\n\nNote content:\n%s\n\n" +
            "Should this operation be performed? Provide analysis.",
            operation,
            truncate(noteContent, 1000)
        );

        return chat(systemMessage, userMessage);
    }

    /**
     * Analyze and fix Org-mode formatting issues
     * Returns the corrected content or null if no changes needed
     */
    public String normalizeOrgFormatting(String noteContent, String noteId) {
        String systemMessage = """
            You are an expert at Org-mode formatting. Your task is to ensure notes follow proper Org-mode conventions.

            Required format:
            1. :PROPERTIES: drawer at the top with at minimum :ID: and :CREATED: properties
            2. A single level-1 heading (* Title) after the properties drawer
            3. Body content after the heading
            4. File must end with a single newline

            Rules:
            - Preserve all existing content - only fix formatting
            - If :PROPERTIES: drawer is missing, add it at the top
            - If :ID: is missing, add it with the provided ID
            - If :CREATED: is missing, add it with current timestamp
            - If title (level-1 heading) is missing, add one based on content
            - Preserve existing properties, links, and body content exactly
            - Ensure proper spacing between sections

            Return ONLY the corrected Org-mode content, with no explanations.
            If the content is already properly formatted, return it unchanged.
            """;

        String userMessage = String.format(
            "Note ID: %s\n\nCurrent content:\n%s\n\nProvide the properly formatted version:",
            noteId,
            noteContent
        );

        return chat(systemMessage, userMessage);
    }

    /**
     * Analyze Org-mode formatting and provide a report of issues
     */
    public String analyzeOrgFormatting(String noteContent) {
        String systemMessage = """
            You are an expert at Org-mode formatting. Analyze the provided note and identify any formatting issues.

            Check for:
            1. Missing or malformed :PROPERTIES: drawer
            2. Missing required properties (:ID:, :CREATED:)
            3. Missing or improper level-1 heading (* Title)
            4. Improper spacing or structure
            5. Missing final newline

            Provide a concise list of issues found, or "No issues" if properly formatted.
            """;

        String userMessage = String.format("Analyze this Org-mode note:\n\n%s", noteContent);

        return chat(systemMessage, userMessage);
    }

    /**
     * Analyze note structure for split/merge optimization.
     * Returns JSON response that can be parsed into StructureAnalysis.
     */
    public String analyzeNoteStructure(String noteContent) {
        String systemMessage = """
            You are an expert at analyzing knowledge base note structure for optimal semantic search.

            Analyze the provided org-roam note and return a JSON response with the following structure:
            {
              "topics": ["topic1", "topic2", ...],
              "coherence": 0.85,
              "tokenCount": 650,
              "optimalSize": true,
              "tooSmall": false,
              "tooLarge": false,
              "splitPoints": [
                {
                  "characterOffset": 450,
                  "beforeHeading": "Docker Basics",
                  "afterHeading": "Kubernetes Overview",
                  "rationale": "Distinct shift from Docker to Kubernetes topic",
                  "confidence": 0.9
                }
              ],
              "recommendation": "Split this note - it covers two distinct topics that would benefit from separation"
            }

            Guidelines:
            - Optimal size is 300-800 tokens
            - Coherence: 1.0 = single focused topic, 0.0 = completely scattered
            - Only suggest split points if multiple distinct topics exist
            - Topics should be semantic clusters, not just sections
            - Split points should be at natural boundaries (heading changes, topic shifts)

            Return ONLY the JSON, no explanations.
            """;

        String userMessage = String.format("Analyze this org-roam note:\n\n%s", noteContent);

        return chat(systemMessage, userMessage);
    }

    /**
     * Estimate token count for a piece of text
     * Simple approximation: ~4 chars per token for English
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.length() / 4;
    }

    /**
     * Truncate text to max length with ellipsis
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
