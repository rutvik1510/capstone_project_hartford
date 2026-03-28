package org.hartford.eventguard.dto;

public class ApproveSubscriptionRequest {
    private Double premiumOverrideAmount;
    private String overrideReason;
    private String underwriterNotes;

    public ApproveSubscriptionRequest() {}

    public String getUnderwriterNotes() {
        return underwriterNotes;
    }

    public void setUnderwriterNotes(String underwriterNotes) {
        this.underwriterNotes = underwriterNotes;
    }

    public Double getPremiumOverrideAmount() {
        return premiumOverrideAmount;
    }

    public void setPremiumOverrideAmount(Double premiumOverrideAmount) {
        this.premiumOverrideAmount = premiumOverrideAmount;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }
}
