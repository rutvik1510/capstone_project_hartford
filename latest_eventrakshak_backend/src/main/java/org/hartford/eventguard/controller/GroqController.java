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

        ChatMessage userMsg = chatMessageRepository.save(new ChatMessage(user, request.getEventId(), request.getUserQuery(), "USER"));

        List<ChatMessage> history;
        if (request.getEventId() != null) {
            history = chatMessageRepository.findByUserAndEventIdOrderByTimestampAsc(user, request.getEventId());
        } else {
            history = chatMessageRepository.findByUserOrderByTimestampAsc(user);
        }
        
        if (history.size() > 10) {
            history = history.subList(history.size() - 10, history.size());
        }

        String prompt = buildStructuredPrompt(request, history);
        String aiRawResponse = groqService.generateContent(prompt);

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
                // Update the user's message with the identified event ID
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
            message = aiRawResponse;
        }

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

    private String buildStructuredPrompt(GeminiChatRequest request, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are 'EventGuard Assistant', an intelligent, professional insurance advisor with FULL ACCESS to the system database.\n");
        sb.append("Your goal is to provide 100% accurate answers based on the current state of the database provided below.\n\n");

        // 1. SYSTEM ENTITY SCHEMA (Help AI understand the structure)
        sb.append("--- DATABASE SCHEMA OVERVIEW ---\n")
          .append("- User: email, fullName, phone, companyName, roles\n")
          .append("- Event: eventName, eventType (OUTDOOR_MUSIC_CONCERT, CORPORATE_TECH_CONFERENCE), budget, attendees, date, venueType, riskFactors\n")
          .append("- Policy: policyName, baseRate, description, domain, coverage (fire, theft, weather, cancellation)\n")
          .append("- PolicySubscription: status (PENDING, PAID, REJECTED, EXPIRED), premiumAmount, riskScores (eventRisk, weatherRisk, totalRisk)\n")
          .append("- Claim: status (PENDING, UNDER_REVIEW, APPROVED, REJECTED, COLLECTED, SETTLED), claimAmount, approvedAmount, incidentDate\n\n");

        // 2. FETCH GLOBAL CONTEXT
        List<Policy> allPolicies = policyRepository.findAll();
        sb.append("--- GLOBAL SYSTEM DATA: AVAILABLE POLICIES ---\n");
        for (Policy p : allPolicies) {
            sb.append(String.format("- [ID:%d] %s: Rate=%.1f%%, Domain=%s, Cov: FIRE=%b, THEFT=%b, WEATH=%b, CANCEL=%b. Desc: %s\n",
                    p.getPolicyId(), p.getPolicyName(), p.getBaseRate(), p.getDomain(), 
                    p.getCoversFire(), p.getCoversTheft(), p.getCoversWeather(), p.getCoversCancelation(), p.getDescription()));
        }
        sb.append("\n");

        // 3. FETCH USER-SPECIFIC CONTEXT
        User user = null;
        try {
            String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            user = userRepository.findByEmail(email).orElse(null);
        } catch(Exception ignored) {}

        if (user != null) {
            String role = user.getRoles().stream().map(r -> r.getRoleName()).findFirst().orElse("CUSTOMER");
            sb.append("--- CURRENT USER SESSION ---\n")
              .append("Name: ").append(user.getFullName()).append("\n")
              .append("Email: ").append(user.getEmail()).append("\n")
              .append("Role: ").append(role).append("\n\n");

            sb.append("--- DATABASE SNAPSHOT (FOR YOUR ROLE: ").append(role).append(") ---\n");

            if (role.contains("ADMIN")) {
                // Admin gets full system visibility
                long totalUsers = userRepository.count();
                Double totalRevenue = subscriptionRepository.sumPaidPremiums();
                Double totalPayouts = claimsRepository.sumApprovedPayouts();

                sb.append("- Total Registered Users: ").append(totalUsers).append("\n")
                  .append("- System Revenue: INR ").append(String.format("%.2f", totalRevenue)).append("\n")
                  .append("- Total Approved Payouts: INR ").append(String.format("%.2f", totalPayouts)).append("\n\n");

                sb.append("- PENDING SUBSCRIPTIONS (Awaiting Review):\n");
                List<PolicySubscription> pendingSubsList = subscriptionRepository.findByStatus(org.hartford.eventguard.entity.SubscriptionStatus.PENDING);
                if (pendingSubsList.isEmpty()) sb.append("  * None\n");
                else {
                    for (PolicySubscription s : pendingSubsList) {
                        sb.append("  * Sub #").append(s.getSubscriptionId()).append(": Event='").append(s.getEvent().getEventName())
                          .append("', User='").append(s.getEvent().getUser().getFullName()).append("', Policy='")
                          .append(s.getPolicy().getPolicyName()).append("', Amount=INR ").append(s.getPremiumAmount()).append("\n");
                    }
                }

                sb.append("\n- PENDING CLAIMS (Awaiting Review):\n");
                List<Claim> pendingClaimsList = claimsRepository.findByStatus(org.hartford.eventguard.entity.ClaimStatus.PENDING);
                if (pendingClaimsList.isEmpty()) sb.append("  * None\n");
                else {
                    for (Claim c : pendingClaimsList) {
                        sb.append("  * Claim #").append(c.getClaimId()).append(": Event='").append(c.getPolicySubscription().getEvent().getEventName())
                          .append("', User='").append(c.getPolicySubscription().getEvent().getUser().getFullName())
                          .append("', Claim Amount=INR ").append(c.getClaimAmount()).append("\n");
                    }
                }

                sb.append("\n- LATEST 5 EVENTS REGISTERED:\n");
                eventRepository.findTop10ByOrderByEventIdDesc().stream().limit(5).forEach(e -> 
                    sb.append("  * ").append(e.getEventName()).append(" (User: ").append(e.getUser().getFullName())
                      .append(", Date: ").append(e.getEventDate()).append(", ID: ").append(e.getEventId()).append(")\n"));

                sb.append("\n- LATEST 5 USERS REGISTERED:\n");
                userRepository.findTop10ByOrderByUserIdDesc().stream().limit(5).forEach(u -> 
                    sb.append("  * ").append(u.getFullName()).append(" (").append(u.getEmail())
                      .append(", Company: ").append(u.getCompanyName()).append(", ID: ").append(u.getUserId()).append(")\n"));

            } else if (role.contains("UNDERWRITER")) {
                List<PolicySubscription> assigned = subscriptionRepository.findByAssignedUnderwriter(user);
                sb.append("- Your Assigned Tasks (Subscriptions to Review):\n");
                for (PolicySubscription s : assigned) {
                    sb.append("  * Sub ID #").append(s.getSubscriptionId()).append(": Event=").append(s.getEvent().getEventName())
                      .append(", Policy=").append(s.getPolicy().getPolicyName()).append(", Status=").append(s.getStatus()).append("\n");
                }

            } else if (role.contains("CLAIMS_OFFICER")) {
                List<Claim> assigned = claimsRepository.findByAssignedOfficer(user);
                sb.append("- Your Assigned Tasks (Claims to Review):\n");
                for (Claim c : assigned) {
                    sb.append("  * Claim ID #").append(c.getClaimId()).append(": Event=").append(c.getPolicySubscription().getEvent().getEventName())
                      .append(", Amount=").append(c.getClaimAmount()).append(", Status=").append(c.getStatus()).append("\n");
                }

            } else {
                // Default Customer View - Extremely Detailed
                List<Event> userEvents = eventRepository.findByUser(user);
                sb.append("- YOUR EVENTS:\n");
                for (Event e : userEvents) {
                    sb.append("  * Event: ").append(e.getEventName()).append(" [ID:").append(e.getEventId()).append("], Type: ").append(e.getEventType())
                      .append(", Date: ").append(e.getEventDate()).append(", Budget: ").append(e.getBudget()).append("\n");
                    
                    List<PolicySubscription> subs = subscriptionRepository.findByEvent_EventId(e.getEventId());
                    for (PolicySubscription s : subs) {
                        sb.append("    > Sub #").append(s.getSubscriptionId()).append(": ").append(s.getPolicy().getPolicyName())
                          .append(", Status=").append(s.getStatus()).append(", Paid=").append(s.isPaid())
                          .append(", Premium=").append(s.getPremiumAmount()).append(", Risk=").append(s.getTotalRisk()).append("%\n");
                        
                        claimsRepository.findByPolicySubscription_SubscriptionId(s.getSubscriptionId()).ifPresent(c -> {
                            sb.append("      + Claim #").append(c.getClaimId()).append(": Status=").append(c.getStatus())
                              .append(", Amount=").append(c.getClaimAmount()).append(", Approved=").append(c.getApprovedAmount())
                              .append(", Incident Date=").append(c.getIncidentDate()).append(", Desc=").append(c.getDescription()).append("\n");
                        });
                    }
                }
                
                List<Notification> notices = notificationRepository.findByRecipientAndIsReadFalseOrderByCreatedAtDesc(user);
                if (!notices.isEmpty()) {
                    sb.append("- UNREAD NOTIFICATIONS:\n");
                    notices.stream().limit(3).forEach(n -> sb.append("  * ").append(n.getMessage()).append("\n"));
                }
            }
        }

        // 4. BUSINESS RULES
        sb.append("\n--- BUSINESS RULES ---\n")
          .append("- 10-DAY RULE: Policies MUST be subscribed at least 10 days before event date.\n")
          .append("- PREMIUM FORMULA: Base Premium = (Budget * Base Rate %) * Risk Multiplier.\n")
          .append("- RISK MULTIPLIERS: 1.3 (Medium), 1.6 (High), 2.0 (Critical).\n")
          .append("- FILING CLAIMS: Only possible if subscription status is 'PAID'.\n")
          .append("- LOCK STATUS: Events are locked once a claim is COLLECTED or SETTLED.\n\n");

        // 5. CURRENT ACTION CONTEXT
        if (request.getEventId() != null) {
            eventRepository.findById(request.getEventId()).ifPresent(event -> {
                sb.append("--- CURRENTLY SELECTED EVENT (ACTIVE CONTEXT) ---\n")
                  .append("Name: ").append(event.getEventName()).append("\n")
                  .append("Budget: INR ").append(event.getBudget()).append("\n")
                  .append("Lead Time: ").append(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), event.getEventDate())).append(" days\n");

                try {
                    DetailedRiskBreakdown rb = riskCalculationService.calculateRiskWithBreakdown(event);
                    sb.append("AI-Assisted Risk Analysis: EventRisk=").append(rb.getEventRisk()).append("%, WeatherRisk=").append(rb.getWeatherRisk())
                      .append("%, Total=").append(rb.getEventRisk() + rb.getWeatherRisk()).append("%, Factors=").append(rb.getRiskFactors()).append("\n");
                } catch (Exception e) {
                    sb.append("Risk assessment currently unavailable for this event.\n");
                }
                sb.append("\n");
            });
        }

        // 6. CONVERSATION HISTORY
        if (!history.isEmpty()) {
            sb.append("--- RECENT CONVERSATION ---\n");
            for (ChatMessage msg : history) {
                sb.append(msg.getSender()).append(": ").append(msg.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("--- USER QUERY ---\n").append(request.getUserQuery()).append("\n\n");

        sb.append("--- FINAL INSTRUCTIONS ---\n")
          .append("- USE THE DATABASE SNAPSHOT ABOVE. If a user asks 'What is my claim status?', look at their claims in the snapshot.\n")
          .append("- NEVER say 'I don't have access to your data'. You DO HAVE IT in the prompt context above.\n")
          .append("- If the user is referring to a specific event (e.g., by name), identify its ID and return it as 'identifiedEventId'.\n")
          .append("- If recommending a policy, use the [ID:x] from the policy list.\n")
          .append("- Be concise (2-3 sentences max).\n")
          .append("- Respond ONLY in this JSON format:\n")
          .append("{\n")
          .append("  \"answer\": \"Detailed answer based on DB data\",\n")
          .append("  \"identifiedEventId\": id_or_null,\n")
          .append("  \"recommendedPolicyId\": id_or_null,\n")
          .append("  \"actions\": [\"Button Label 1\", \"Button Label 2\"]\n")
          .append("}");

        return sb.toString();
    }
}
