package org.hartford.eventguard.service;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GroqService {

    private static final Logger logger = LoggerFactory.getLogger(GroqService.class);
    private final ChatLanguageModel chatModel;
    private final Assistant assistant;

    public interface Assistant {
        @SystemMessage({
            "You are a professional business assistant for EventGuard Insurance.",
            "User Role: {{role}}.",
            "",
            "CORE CAPABILITIES:",
            "1. Tracking Claims: Use 'getMyClaims' to list claims and 'getClaimDetails' for specific info.",
            "2. Document Analysis: Use 'analyzeClaimEvidence' for claim verification or 'analyzeEventSafetyDoc' for event safety documents.",
            "3. General Queries: Use 'InsuranceTools' to check events, policies, and stats.",
            "4. Knowledge Base: Use RAG context ONLY if the user asks for definitions or general insurance advice.",
            "",
            "CORE RULES:",
            "1. ALWAYS use Tools to check the database first.",
            "2. If a user asks 'What is the status of my claim?', call 'getMyClaims' first.",
            "3. If a user asks to 'analyze the PDF' or 'check evidence' for a claim, call 'analyzeClaimEvidence'.",
            "4. If a user asks about 'safety docs' or 'event compliance', call 'analyzeEventSafetyDoc'.",
            "5. If no data is found, say: 'I couldn't find that information in the system.'",
            "6. NEVER mention technical terms like RAG, Tools, or JSON.",
            "7. IMPORTANT: All IDs (claimId, eventId) MUST be numeric (e.g., 101).",
            "8. NEVER guess an ID. If you don't know the ID, call 'getMyClaims' or 'getMyEvents' first to find it.",
            "9. NEVER use placeholder strings like 'myClaimId' or 'id' for tool parameters.",
            "",
            "Response MUST be valid JSON:",
            "{ \"answer\": \"your response\", \"actions\": [] }"
        })
        String chat(@V("role") String role, @UserMessage String userMessage);
    }

    public GroqService(@Value("${groq.api.key}") String apiKey,
                       @Value("${groq.api.url}") String apiUrl,
                       @Value("${groq.model}") String model,
                       InsuranceTools insuranceTools,
                       RAGService ragService) {
        
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(apiUrl)
                .modelName(model)
                .temperature(0.0)
                .build();

        this.assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(5))
                .tools(insuranceTools)
                .contentRetriever(query -> {
                    // Optimized: Only fetch the top 1 most relevant chunk to save tokens
                    String context = ragService.findRelevantContext(query.text());
                    return context == null || context.isBlank() ? java.util.Collections.emptyList() : 
                           java.util.Collections.singletonList(dev.langchain4j.rag.content.Content.from(context));
                })
                .build();
    }

    public String generateContent(String role, String userQuery) {
        try {
            return assistant.chat(role, userQuery);
        } catch (Exception e) {
            // FORCE PRINT ERROR TO TERMINAL
            System.err.println("!!! AI ENGINE CRASH: " + e.getMessage());
            e.printStackTrace();
            return "{\"answer\": \"I am having trouble connecting to my brain right now. Please check your API key or try again in a minute.\", \"actions\": []}";
        }
    }
}
