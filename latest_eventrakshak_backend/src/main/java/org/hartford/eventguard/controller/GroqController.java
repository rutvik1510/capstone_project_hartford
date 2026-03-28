package org.hartford.eventguard.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hartford.eventguard.dto.ApiResponse;
import org.hartford.eventguard.dto.GeminiChatRequest;
import org.hartford.eventguard.dto.GeminiChatResponse;
import org.hartford.eventguard.entity.ChatMessage;
import org.hartford.eventguard.entity.Claim;
import org.hartford.eventguard.entity.Event;
import org.hartford.eventguard.entity.Policy;
import org.hartford.eventguard.entity.PolicySubscription;
import org.hartford.eventguard.entity.User;
import org.hartford.eventguard.repo.*;
import org.hartford.eventguard.service.DocumentAnalysisService;
import org.hartford.eventguard.service.GroqService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
public class GroqController {

    private final GroqService groqService;
    private final DocumentAnalysisService documentAnalysisService;
    private final EventRepository eventRepository;
    private final PolicyRepository policyRepository;
    private final PolicySubscriptionRepository subscriptionRepository;
    private final ClaimsRepository claimsRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GroqController(GroqService groqService,
                          DocumentAnalysisService documentAnalysisService,
                          EventRepository eventRepository,
                          PolicyRepository policyRepository,
                          PolicySubscriptionRepository subscriptionRepository,
                          ClaimsRepository claimsRepository,
                          ChatMessageRepository chatMessageRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper) {
        this.groqService = groqService;
        this.documentAnalysisService = documentAnalysisService;
        this.eventRepository = eventRepository;
        this.policyRepository = policyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.claimsRepository = claimsRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getHistory(Authentication authentication, 
                                                                    @RequestParam(required = false) Long eventId) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<ChatMessage> history;
        if (eventId != null) {
            history = chatMessageRepository.findByUserAndEventIdOrderByTimestampAsc(user, eventId);
        } else {
            history = chatMessageRepository.findByUserOrderByTimestampAsc(user);
        }
        
        return ResponseEntity.ok(ApiResponse.success("History retrieved", history));
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<GeminiChatResponse>> chat(@RequestBody GeminiChatRequest request, 
                                                               Authentication authentication) {
        if (request.getUserQuery() == null || request.getUserQuery().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Query cannot be empty"));
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        chatMessageRepository.save(new ChatMessage(user, request.getEventId(), request.getUserQuery(), "USER"));

        List<ChatMessage> history = chatMessageRepository.findByUserAndEventIdOrderByTimestampAsc(user, request.getEventId());
        if (history.size() > 10) {
            history = history.subList(history.size() - 10, history.size());
        }

        String prompt = buildStructuredPrompt(request, history);
        String aiRawResponse = groqService.generateContent(prompt);

        String message;
        Long suggestedPolicyId = null;

        try {
            String jsonContent = aiRawResponse.trim();
            if (jsonContent.startsWith("```json")) {
                jsonContent = jsonContent.substring(7, jsonContent.length() - 3).trim();
            } else if (jsonContent.startsWith("```")) {
                jsonContent = jsonContent.substring(3, jsonContent.length() - 3).trim();
            }

            JsonNode root = objectMapper.readTree(jsonContent);
            message = root.path("answer").asText();
            suggestedPolicyId = root.has("recommendedPolicyId") && !root.path("recommendedPolicyId").isNull() 
                    ? root.path("recommendedPolicyId").asLong() : null;

        } catch (Exception e) {
            message = aiRawResponse;
        }

        chatMessageRepository.save(new ChatMessage(user, request.getEventId(), message, "AI"));

        return ResponseEntity.ok(ApiResponse.success("Response received", new GeminiChatResponse(message, suggestedPolicyId)));
    }

    @GetMapping("/analyze/subscription/{id}")
    public ResponseEntity<ApiResponse<String>> analyzeSubscriptionDoc(@PathVariable Long id) {
        PolicySubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        
        String analysis = documentAnalysisService.analyzeDocument(sub.getEvent().getSafetyComplianceDocPath(), "SUBSCRIPTION");
        return ResponseEntity.ok(ApiResponse.success("Analysis complete", analysis));
    }

    @GetMapping("/analyze/claim/{id}")
    public ResponseEntity<ApiResponse<String>> analyzeClaimDoc(@PathVariable Long id) {
        Claim claim = claimsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found"));
        
        String analysis = documentAnalysisService.analyzeDocument(claim.getEvidenceDocPath(), "CLAIM");
        return ResponseEntity.ok(ApiResponse.success("Analysis complete", analysis));
    }

    private String buildStructuredPrompt(GeminiChatRequest request, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are 'EventGuard Assistant', a professional and extremely concise insurance advisor.\n\n");

        if (request.getEventId() != null) {
            eventRepository.findById(request.getEventId()).ifPresent(event -> {
                sb.append("--- EVENT CONTEXT ---\n")
                  .append("Name: ").append(event.getEventName()).append("\n")
                  .append("Budget: INR ").append(event.getBudget()).append("\n")
                  .append("Attendees: ").append(event.getNumberOfAttendees()).append("\n\n");
            });
        }

        if (!history.isEmpty()) {
            sb.append("--- RECENT CONVERSATION ---\n");
            for (ChatMessage msg : history) {
                sb.append(msg.getSender()).append(": ").append(msg.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        List<Policy> policies = policyRepository.findAll();
        sb.append("--- AVAILABLE POLICIES ---\n");
        for (Policy p : policies) {
            sb.append("- ID: ").append(p.getPolicyId()).append(", Name: ").append(p.getPolicyName()).append("\n");
        }
        sb.append("\n");

        sb.append("--- USER QUERY ---\n").append(request.getUserQuery()).append("\n\n");

        sb.append("--- INSTRUCTIONS ---\n")
          .append("- Be very brief. Focus ONLY on the most important parts.\n")
          .append("- Avoid long introductory phrases.\n")
          .append("- Respond ONLY in this JSON format:\n")
          .append("{\n")
          .append("  \"answer\": \"your concise explanation here\",\n")
          .append("  \"recommendedPolicyId\": policy_id_or_null\n")
          .append("}");

        return sb.toString();
    }
}
