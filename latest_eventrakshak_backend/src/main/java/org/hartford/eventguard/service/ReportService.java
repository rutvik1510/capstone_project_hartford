package org.hartford.eventguard.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.hartford.eventguard.entity.Claim;
import org.hartford.eventguard.entity.PolicySubscription;
import org.hartford.eventguard.exception.ResourceNotFoundException;
import org.hartford.eventguard.repo.ClaimsRepository;
import org.hartford.eventguard.repo.PolicySubscriptionRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReportService {

    private final PolicySubscriptionRepository subscriptionRepository;
    private final ClaimsRepository claimsRepository;

    private static final Color PRIMARY_COLOR = new Color(31, 41, 55); 
    private static final Color ACCENT_COLOR = new Color(140, 29, 64);  
    private static final Color BG_LIGHT = new Color(249, 250, 251);
    private static final float LEFT_MARGIN = 50;
    private static final float RIGHT_MARGIN = 562;
    private static final float VALUE_OFFSET = 180; // Distance from label to value

    public ReportService(PolicySubscriptionRepository subscriptionRepository, ClaimsRepository claimsRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.claimsRepository = claimsRepository;
    }

    public byte[] generatePolicyReport(Long subscriptionId) throws IOException {
        PolicySubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                drawHeader(contentStream, "Premium Payment Receipt", "RCP-SUB-" + String.format("%06d", subscriptionId));

                float y = 660;
                
                // Section: Transaction Summary
                y = drawSectionTitle(contentStream, y, "Payment Summary");
                y -= 25;
                drawReceiptRow(contentStream, y, "Receipt Date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
                y -= 18;
                drawReceiptRow(contentStream, y, "Payment Status", sub.getStatus().toString());
                y -= 18;
                drawReceiptRow(contentStream, y, "Total Premium Paid", "INR " + String.format("%,.2f", sub.getPremiumAmount()));
                y -= 35;

                // Section: Customer Info
                y = drawSectionTitle(contentStream, y, "Customer Information");
                y -= 25;
                drawReceiptRow(contentStream, y, "Policy Holder", sub.getEvent().getUser().getFullName());
                y -= 18;
                drawReceiptRow(contentStream, y, "Email ID", sub.getEvent().getUser().getEmail());
                y -= 35;

                // Section: Coverage Details
                y = drawSectionTitle(contentStream, y, "Coverage & Policy Details");
                y -= 25;
                drawReceiptRow(contentStream, y, "Plan Name", sub.getPolicy().getPolicyName());
                y -= 18;
                drawReceiptRow(contentStream, y, "Event Name", sub.getEvent().getEventName());
                y -= 18;
                drawReceiptRow(contentStream, y, "Max Sum Insured", "INR " + String.format("%,.2f", sub.getPolicy().getMaxCoverageAmount()));
                y -= 18;
                drawReceiptRow(contentStream, y, "Risk Assessment", String.format("%.2f", sub.getRiskPercentage()) + "%");
                y -= 35;

                // Status Badge
                if ("PAID".equalsIgnoreCase(sub.getStatus().toString())) {
                   drawStatusBadge(contentStream, 450, 680, "PAID", new Color(22, 163, 74));
                }

                drawFooter(contentStream);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    public byte[] generateClaimReport(Long claimId) throws IOException {
        Claim claim = claimsRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                drawHeader(contentStream, "Claim Settlement Receipt", "SET-CLM-" + String.format("%06d", claimId));

                float y = 660;

                // Section: Settlement Summary
                y = drawSectionTitle(contentStream, y, "Settlement Summary");
                y -= 25;
                drawReceiptRow(contentStream, y, "Settlement Date", claim.getResolvedAt() != null ? claim.getResolvedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "Pending");
                y -= 18;
                drawReceiptRow(contentStream, y, "Claim Status", claim.getStatus().toString());
                y -= 18;
                drawReceiptRow(contentStream, y, "Net Approved Amount", "INR " + String.format("%,.2f", (claim.getApprovedAmount() != null ? claim.getApprovedAmount() : 0.0)));
                y -= 35;

                // Section: Claim Context
                y = drawSectionTitle(contentStream, y, "Claim Information");
                y -= 25;
                drawReceiptRow(contentStream, y, "Event Name", claim.getPolicySubscription().getEvent().getEventName());
                y -= 18;
                drawReceiptRow(contentStream, y, "Incident Date", claim.getIncidentDate().toString());
                y -= 18;
                drawReceiptRow(contentStream, y, "Reported Amount", "INR " + String.format("%,.2f", claim.getClaimAmount()));
                y -= 35;
                
                // Section: Remarks
                if (claim.getInternalRemarks() != null && !claim.getInternalRemarks().isEmpty()) {
                    y = drawSectionTitle(contentStream, y, "Settlement Remarks");
                    y -= 25;
                    drawLongText(contentStream, LEFT_MARGIN, y, claim.getInternalRemarks());
                }

                // Status Badge
                if ("COLLECTED".equalsIgnoreCase(claim.getStatus().toString()) || "PAID".equalsIgnoreCase(claim.getStatus().toString())) {
                    drawStatusBadge(contentStream, 450, 680, "SETTLED", new Color(37, 99, 235));
                }

                drawFooter(contentStream);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void drawHeader(PDPageContentStream contentStream, String title, String refNo) throws IOException {
        // Top accent
        contentStream.setNonStrokingColor(ACCENT_COLOR);
        contentStream.addRect(0, 785, 612, 15);
        contentStream.fill();

        // Logo Section
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 24);
        contentStream.newLineAtOffset(LEFT_MARGIN, 750);
        contentStream.showText("EventGuard");
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.setNonStrokingColor(Color.GRAY);
        contentStream.newLineAtOffset(LEFT_MARGIN, 735);
        contentStream.showText("Official Digital Receipt");
        contentStream.endText();

        // Receipt/Ref No on Right
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.newLineAtOffset(400, 750);
        contentStream.showText("REFERENCE: " + refNo);
        contentStream.endText();

        // Main Title
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
        contentStream.newLineAtOffset(LEFT_MARGIN, 695);
        contentStream.showText(title);
        contentStream.endText();

        contentStream.setStrokingColor(new Color(230, 230, 230));
        contentStream.moveTo(LEFT_MARGIN, 685);
        contentStream.lineTo(RIGHT_MARGIN, 685);
        contentStream.stroke();
    }

    private float drawSectionTitle(PDPageContentStream contentStream, float y, String title) throws IOException {
        contentStream.setNonStrokingColor(BG_LIGHT);
        contentStream.addRect(LEFT_MARGIN, y - 5, RIGHT_MARGIN - LEFT_MARGIN, 20);
        contentStream.fill();

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.setNonStrokingColor(ACCENT_COLOR);
        contentStream.newLineAtOffset(LEFT_MARGIN + 5, y);
        contentStream.showText(title.toUpperCase());
        contentStream.endText();
        
        return y;
    }

    private void drawReceiptRow(PDPageContentStream contentStream, float y, String label, String value) throws IOException {
        // Label
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.setNonStrokingColor(new Color(107, 114, 128)); // Slate-500
        contentStream.newLineAtOffset(LEFT_MARGIN + 5, y);
        contentStream.showText(label);
        contentStream.endText();

        // Dotted Line (optional visual)
        // Value
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.newLineAtOffset(LEFT_MARGIN + VALUE_OFFSET, y);
        contentStream.showText(value != null ? value : "N/A");
        contentStream.endText();
    }

    private void drawStatusBadge(PDPageContentStream contentStream, float x, float y, String text, Color color) throws IOException {
        contentStream.setNonStrokingColor(color);
        contentStream.addRect(x, y, 80, 22);
        contentStream.fill();

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
        contentStream.setNonStrokingColor(Color.WHITE);
        contentStream.newLineAtOffset(x + 15, y + 7);
        contentStream.showText(text);
        contentStream.endText();
    }

    private void drawLongText(PDPageContentStream contentStream, float x, float y, String text) throws IOException {
        if (text == null) return;
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.setNonStrokingColor(PRIMARY_COLOR);
        contentStream.newLineAtOffset(x + 5, y);
        if (text.length() > 90) {
            contentStream.showText(text.substring(0, 87) + "...");
        } else {
            contentStream.showText(text);
        }
        contentStream.endText();
    }

    private void drawFooter(PDPageContentStream contentStream) throws IOException {
        contentStream.setStrokingColor(new Color(230, 230, 230));
        contentStream.moveTo(LEFT_MARGIN, 100);
        contentStream.lineTo(RIGHT_MARGIN, 100);
        contentStream.stroke();

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 8);
        contentStream.setNonStrokingColor(Color.GRAY);
        contentStream.newLineAtOffset(LEFT_MARGIN, 85);
        contentStream.showText("This is an electronically generated receipt and does not require a physical signature.");
        contentStream.endText();

        contentStream.beginText();
        contentStream.newLineAtOffset(LEFT_MARGIN, 75);
        contentStream.showText("EventGuard Insurance Platform | Support: support@eventguard.com");
        contentStream.endText();

        // Authorized stamp placeholder
        contentStream.setStrokingColor(ACCENT_COLOR);
        contentStream.addRect(420, 45, 120, 40);
        contentStream.stroke();
        
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 8);
        contentStream.setNonStrokingColor(ACCENT_COLOR);
        contentStream.newLineAtOffset(435, 60);
        contentStream.showText("AUTHORIZED STAMP");
        contentStream.endText();
    }

    private void drawText(PDPageContentStream contentStream, float x, float y, String text) throws IOException {
        if (text == null) text = "N/A";
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
}
