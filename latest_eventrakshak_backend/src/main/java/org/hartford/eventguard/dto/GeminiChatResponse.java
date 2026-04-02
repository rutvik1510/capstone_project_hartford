package org.hartford.eventguard.dto;

import java.util.List;

public class GeminiChatResponse {
    private String message;
    private Long suggestedPolicyId;
    private Long identifiedEventId;
    private List<String> actions;

    public GeminiChatResponse() {}

    public GeminiChatResponse(String message, Long suggestedPolicyId) {
        this.message = message;
        this.suggestedPolicyId = suggestedPolicyId;
    }

    public GeminiChatResponse(String message, Long suggestedPolicyId, List<String> actions) {
        this.message = message;
        this.suggestedPolicyId = suggestedPolicyId;
        this.actions = actions;
    }

    public GeminiChatResponse(String message, Long suggestedPolicyId, Long identifiedEventId, List<String> actions) {
        this.message = message;
        this.suggestedPolicyId = suggestedPolicyId;
        this.identifiedEventId = identifiedEventId;
        this.actions = actions;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getSuggestedPolicyId() {
        return suggestedPolicyId;
    }

    public void setSuggestedPolicyId(Long suggestedPolicyId) {
        this.suggestedPolicyId = suggestedPolicyId;
    }

    public Long getIdentifiedEventId() {
        return identifiedEventId;
    }

    public void setIdentifiedEventId(Long identifiedEventId) {
        this.identifiedEventId = identifiedEventId;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }
}
