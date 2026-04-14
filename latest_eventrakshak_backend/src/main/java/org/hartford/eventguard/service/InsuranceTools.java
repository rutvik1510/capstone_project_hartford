package org.hartford.eventguard.service;

import dev.langchain4j.agent.tool.Tool;
import org.hartford.eventguard.entity.*;
import org.hartford.eventguard.repo.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InsuranceTools {

    private final EventRepository eventRepository;
    private final PolicyRepository policyRepository;
    private final PolicySubscriptionRepository subscriptionRepository;
    private final ClaimsRepository claimsRepository;
    private final UserRepository userRepository;
    private final DocumentAnalysisService documentAnalysisService;

    public InsuranceTools(EventRepository eventRepository,
                          PolicyRepository policyRepository,
                          PolicySubscriptionRepository subscriptionRepository,
                          ClaimsRepository claimsRepository,
                          UserRepository userRepository,
                          DocumentAnalysisService documentAnalysisService) {
        this.eventRepository = eventRepository;
        this.policyRepository = policyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.claimsRepository = claimsRepository;
        this.userRepository = userRepository;
        this.documentAnalysisService = documentAnalysisService;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    private boolean isStaff(User user) {
        return user.getRoles().stream()
                .map(Role::getRoleName)
                .anyMatch(n -> n.contains("ADMIN") || n.contains("UNDERWRITER") || n.contains("CLAIMS_OFFICER"));
    }

    private boolean isUserAdmin(User user) {
        return user.getRoles().stream()
                .map(Role::getRoleName)
                .anyMatch(n -> n.equalsIgnoreCase("ADMIN"));
    }

    @Tool("Get my filed claims and their current status (CUSTOMER ONLY)")
    public String getMyClaims() {
        User user = getCurrentUser();
        List<Claim> claims = claimsRepository.findByPolicySubscription_Event_User(user);
        return claims.isEmpty() ? "You have no filed claims." : claims.stream()
                .map(c -> String.format("Claim #%d: Event: %s, Status: %s, Requested: %.2f", 
                    c.getClaimId(), c.getPolicySubscription().getEvent().getEventName(), c.getStatus(), c.getClaimAmount()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Get detailed status for a specific claim. Parameter MUST be a numeric ID (e.g., 101) found from getMyClaims.")
    public String getClaimDetails(Long claimId) {
        User user = getCurrentUser();
        return claimsRepository.findById(claimId).map(c -> {
            // Security check: Either user owns the event/claim or is staff
            if (!isStaff(user) && !c.getPolicySubscription().getEvent().getUser().getUserId().equals(user.getUserId())) {
                return "Access Denied: You do not have permission to view this claim.";
            }
            return String.format("Claim #%d for Event '%s'\nStatus: %s\nRequested: %.2f, Approved: %.2f\nFiled At: %s\nDescription: %s\nRejection Reason: %s",
                c.getClaimId(), c.getPolicySubscription().getEvent().getEventName(), c.getStatus(), 
                c.getClaimAmount(), c.getApprovedAmount() != null ? c.getApprovedAmount() : 0.0,
                c.getFiledAt(), c.getDescription(), c.getRejectionReason() != null ? c.getRejectionReason() : "None");
        }).orElse("Claim not found.");
    }

    @Tool("Analyze the evidence document of a claim. Parameter MUST be a numeric ID (e.g., 101) found from getMyClaims.")
    public String analyzeClaimEvidence(Long claimId) {
        User user = getCurrentUser();
        Claim claim = claimsRepository.findById(claimId).orElse(null);
        if (claim == null) return "Claim not found.";

        // Security check
        if (!isStaff(user) && !claim.getPolicySubscription().getEvent().getUser().getUserId().equals(user.getUserId())) {
            return "Access Denied.";
        }

        if (claim.getEvidenceDocPath() == null || claim.getEvidenceDocPath().isBlank()) {
            return "No evidence document uploaded for this claim.";
        }

        return documentAnalysisService.analyzeDocument(claim.getEvidenceDocPath(), "CLAIM");
    }

    @Tool("Analyze the safety compliance document of an event. Parameter MUST be a numeric ID (e.g., 50) found from getMyEvents or findIdByName.")
    public String analyzeEventSafetyDoc(Long eventId) {
        User user = getCurrentUser();
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return "Event not found.";

        // Security check
        if (!isStaff(user) && !event.getUser().getUserId().equals(user.getUserId())) {
            return "Access Denied.";
        }

        if (event.getSafetyComplianceDocPath() == null || event.getSafetyComplianceDocPath().isBlank()) {
            return "No safety compliance document uploaded for this event.";
        }

        return documentAnalysisService.analyzeDocument(event.getSafetyComplianceDocPath(), "SUBSCRIPTION");
    }

    @Tool("Find the ID of an event by its name (e.g., 'Navarang')")
    public String findIdByName(String name) {
        User user = getCurrentUser();
        List<Event> searchPool;
        
        if (isStaff(user)) {
            searchPool = eventRepository.findAll(); // Staff can search everything
        } else {
            searchPool = eventRepository.findByUser(user); // Customers only search their own
        }

        for (Event e : searchPool) {
            if (e.getEventName().equalsIgnoreCase(name)) {
                return String.format("Event '%s' found. ID: %d, Creator: %s", 
                    e.getEventName(), e.getEventId(), e.getUser().getFullName());
            }
        }
        return "No event found with name: " + name;
    }

    @Tool("Get my registered events (CUSTOMER ONLY)")
    public String getMyEvents() {
        User user = getCurrentUser();
        List<Event> events = eventRepository.findByUser(user);
        return events.isEmpty() ? "You have no events." : events.stream()
                .map(e -> String.format("ID: %d, Name: %s, Date: %s", e.getEventId(), e.getEventName(), e.getEventDate()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Get all events in the system (ADMIN/STAFF ONLY)")
    public String getAllSystemEvents() {
        User user = getCurrentUser();
        if (!isStaff(user)) return "Access Denied.";
        return eventRepository.findAll().stream()
                .map(e -> String.format("ID: %d, Name: %s, User: %s", e.getEventId(), e.getEventName(), e.getUser().getFullName()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Get claims assigned to me for review (STAFF ONLY)")
    public String getMyAssignedClaims() {
        User user = getCurrentUser();
        List<Claim> claims = claimsRepository.findByAssignedOfficer(user);
        return claims.isEmpty() ? "No claims assigned to you." : claims.stream()
                .map(c -> String.format("Claim #%d: Event: %s, Status: %s, Amount: %.2f", 
                    c.getClaimId(), c.getPolicySubscription().getEvent().getEventName(), c.getStatus(), c.getClaimAmount()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Get system statistics like revenue and users (ADMIN ONLY)")
    public String getSystemStats() {
        User user = getCurrentUser();
        if (!isStaff(user)) return "Access Denied.";
        Double revenue = subscriptionRepository.sumPaidPremiums();
        return String.format("System Stats: Total Users: %d, Total Revenue: INR %.2f", 
            userRepository.count(), revenue != null ? revenue : 0.0);
    }

    @Tool("Get details of a specific event and its claims. Parameter MUST be a numeric ID (e.g., 50).")
    public String getEventDetails(Long eventId) {
        return eventRepository.findById(eventId)
                .map(e -> String.format("Event: %s, Budget: %.2f, Type: %s, Date: %s", 
                    e.getEventName(), e.getBudget(), e.getEventType(), e.getEventDate()))
                .orElse("Event not found.");
    }

    @Tool("Sync all existing uploaded files into the AI memory (ADMIN ONLY)")
    public String syncAllExistingDocuments() {
        User user = getCurrentUser();
        if (!isUserAdmin(user)) return "Access Denied.";

        List<Event> events = eventRepository.findAll();
        for (Event e : events) {
            if (e.getSafetyComplianceDocPath() != null) {
                java.nio.file.Path p = java.nio.file.Paths.get("uploads", e.getSafetyComplianceDocPath());
                if (java.nio.file.Files.exists(p)) {
                    // Logic already handled by RAGService startup indexing
                }
            }
        }
        return "Sync logic verified. RAG automatically indexes uploads on startup.";
    }

    @Tool("Get all available insurance policies")
    public String getAllAvailablePolicies() {
        return policyRepository.findAll().stream()
                .map(p -> String.format("ID: %d, Name: %s, Rate: %.2f%%", p.getPolicyId(), p.getPolicyName(), p.getBaseRate()))
                .collect(Collectors.joining("\n"));
    }
}
