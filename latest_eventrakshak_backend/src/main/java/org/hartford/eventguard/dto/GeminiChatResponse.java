package org.hartford.eventguard.dto;

public class GeminiChatResponse {
    private String message;
    private Long suggestedPolicyId;

    public GeminiChatResponse() {}

    public GeminiChatResponse(String message, Long suggestedPolicyId) {
        this.message = message;
        this.suggestedPolicyId = suggestedPolicyId;
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
}
