package org.hartford.eventguard.dto;

public class ClaimUpdateRequest {
    private String internalRemarks;
    private String verificationChecklist;

    public String getInternalRemarks() {
        return internalRemarks;
    }

    public void setInternalRemarks(String internalRemarks) {
        this.internalRemarks = internalRemarks;
    }

    public String getVerificationChecklist() {
        return verificationChecklist;
    }

    public void setVerificationChecklist(String verificationChecklist) {
        this.verificationChecklist = verificationChecklist;
    }
}
