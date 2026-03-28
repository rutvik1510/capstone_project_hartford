package org.hartford.eventguard.dto;

public class ApproveClaimRequest {
    private Double approvedAmount;
    private String internalRemarks;

    public ApproveClaimRequest() {}

    public Double getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(Double approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public String getInternalRemarks() {
        return internalRemarks;
    }

    public void setInternalRemarks(String internalRemarks) {
        this.internalRemarks = internalRemarks;
    }
}
