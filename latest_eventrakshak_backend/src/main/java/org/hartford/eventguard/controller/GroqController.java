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
import org.hartford.eventguard.entity.Notification;
import org.hartford.eventguard.repo.*;
import org.hartford.eventguard.service.DocumentAnalysisService;
import org.hartford.eventguard.service.GroqService;
import org.hartford.eventguard.service.RiskCalculationService;
import org.hartford.eventguard.service.RAGService;
import org.hartford.eventguard.service.DetailedRiskBreakdown;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final NotificationRepository notificationRepository;
    private final RiskCalculationService riskCalculationService;
    private final RAGService ragService;
    private final ObjectMapper objectMapper;

    public GroqController(GroqService groqService,
                          DocumentAnalysisService documentAnalysisService,
                          EventRepository eventRepository,
                          PolicyRepository policyRepository,
                          PolicySubscriptionRepository subscriptionRepository,
                          ClaimsRepository claimsRepository,
                          ChatMessageRepository chatMessageRepository,
                          UserRepository userRepository,
                          NotificationRepository notificationRepository,
                          RiskCalculationService riskCalculationService,
                          RAGService ragService,
                          ObjectMapper objectMapper) {
        this.groqService = groqService;
        this.documentAnalysisService = documentAnalysisService;
        this.eventRepository = eventRepository;
        this.policyRepository = policyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.claimsRepository = claimsRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.riskCalculationService = riskCalculationService;
        this.ragService = ragService;
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

        String role = user.getRoles().stream().map(r -> r.getRoleName()).findFirst().orElse("ROLE_CUSTOMER");

        // Save user message to history
        ChatMessage userMsg = chatMessageRepository.save(new ChatMessage(user, request.getEventId(), request.getUserQuery(), "USER"));

        // Let the Agentic GroqService handle reasoning, tool use, and RAG retrieval
        String aiRawResponse = groqService.generateContent(role, request.getUserQuery());

        String message;
        Long suggestedPolicyId = null;
        Long identifiedEventId = request.getEventId();
        java.util.List<String> actions = null;

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
            
            if (root.has("identifiedEventId") && !root.path("identifiedEventId").isNull() && identifiedEventId == null) {
                identifiedEventId = root.path("identifiedEventId").asLong();
                userMsg.setEventId(identifiedEventId);
                chatMessageRepository.save(userMsg);
            }

            if (root.has("actions") && root.path("actions").isArray()) {
                actions = new java.util.ArrayList<>();
                for (JsonNode action : root.path("actions")) {
                    actions.add(action.asText());
                }
            }

        } catch (Exception e) {
            message = aiRawResponse; // Fallback if not JSON
        }

        // Save AI response to history
        chatMessageRepository.save(new ChatMessage(user, identifiedEventId, message, "AI"));

        return ResponseEntity.ok(ApiResponse.success("Response received", new GeminiChatResponse(message, suggestedPolicyId, identifiedEventId, actions)));
    }

    @GetMapping("/analyze/subscription/{id}")
    public ResponseEntity<ApiResponse<String>> analyzeSubscriptionDoc(@PathVariable Long id) {
        PolicySubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        
        String analysis = documentAnalysisService.analyzeSubscriptionWithContext(sub);
        return ResponseEntity.ok(ApiResponse.success("Analysis complete", analysis));
    }

    @GetMapping("/analyze/claim/{id}")
    public ResponseEntity<ApiResponse<String>> analyzeClaimDoc(@PathVariable Long id) {
        Claim claim = claimsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found"));
        
        String analysis = documentAnalysisService.analyzeDocument(claim.getEvidenceDocPath(), "CLAIM");
        return ResponseEntity.ok(ApiResponse.success("Analysis complete", analysis));
    }
}
