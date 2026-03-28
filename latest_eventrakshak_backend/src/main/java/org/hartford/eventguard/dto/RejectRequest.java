package org.hartford.eventguard.dto;

public class RejectRequest {
    private String reason;
    private String underwriterNotes;
    private String internalRemarks;

    public RejectRequest() {}

    public String getInternalRemarks() {
        return internalRemarks;
    }

    public void setInternalRemarks(String internalRemarks) {
        this.internalRemarks = internalRemarks;
    }

    public String getUnderwriterNotes() {
        return underwriterNotes;
    }

    public void setUnderwriterNotes(String underwriterNotes) {
        this.underwriterNotes = underwriterNotes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
