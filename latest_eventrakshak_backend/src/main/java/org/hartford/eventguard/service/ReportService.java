package org.hartford.eventguard.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.hartford.eventguard.entity.Claim;
import org.hartford.eventguard.entity.PolicySubscription;
import org.hartford.eventguard.exception.ResourceNotFoundException;
import org.hartford.eventguard.repo.ClaimsRepository;
import org.hartford.eventguard.repo.PolicySubscriptionRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
public class ReportService {

    private final PolicySubscriptionRepository subscriptionRepository;
    private final ClaimsRepository claimsRepository;

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
                drawHeader(contentStream, "Insurance Policy Certificate");

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                contentStream.newLineAtOffset(50, 680);
                contentStream.showText("Policy Holder Details");
                contentStream.endText();

                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                drawText(contentStream, 50, 660, "Customer Name: " + sub.getEvent().getUser().getFullName());
                drawText(contentStream, 50, 645, "Email: " + sub.getEvent().getUser().getEmail());

                drawSectionTitle(contentStream, 50, 610, "Event Details");
                drawText(contentStream, 50, 590, "Event Name: " + sub.getEvent().getEventName());
                drawText(contentStream, 50, 575, "Event Date: " + sub.getEvent().getEventDate());
                drawText(contentStream, 50, 560, "Location: " + sub.getEvent().getLocation());
                drawText(contentStream, 50, 545, "Budget: INR " + sub.getEvent().getBudget());

                drawSectionTitle(contentStream, 50, 510, "Policy & Premium Details");
                drawText(contentStream, 50, 490, "Policy Name: " + sub.getPolicy().getPolicyName());
                drawText(contentStream, 50, 475, "Base Rate: " + sub.getPolicy().getBaseRate() + "%");
                drawText(contentStream, 50, 460, "Max Coverage: INR " + sub.getPolicy().getMaxCoverageAmount());
                drawText(contentStream, 50, 445, "Premium Amount Paid: INR " + sub.getPremiumAmount());
                drawText(contentStream, 50, 430, "Risk Score: " + String.format("%.2f", sub.getRiskPercentage()) + "%");
                drawText(contentStream, 50, 415, "Status: " + sub.getStatus());

                if (sub.getUnderwriterNotes() != null && !sub.getUnderwriterNotes().isEmpty()) {
                    drawSectionTitle(contentStream, 50, 380, "Underwriter Notes");
                    drawText(contentStream, 50, 360, sub.getUnderwriterNotes());
                }

                if (sub.getApprovedAt() != null) {
                    drawText(contentStream, 50, 320, "Certified On: " + sub.getApprovedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
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
                drawHeader(contentStream, "Claim Settlement Report");

                drawSectionTitle(contentStream, 50, 680, "Claim Details");
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                drawText(contentStream, 50, 660, "Claim ID: #" + claim.getClaimId());
                drawText(contentStream, 50, 645, "Event Name: " + claim.getPolicySubscription().getEvent().getEventName());
                drawText(contentStream, 50, 630, "Incident Date: " + claim.getIncidentDate());
                drawText(contentStream, 50, 615, "Claimed Amount: INR " + claim.getClaimAmount());

                drawSectionTitle(contentStream, 50, 580, "Settlement Details");
                drawText(contentStream, 50, 560, "Status: " + claim.getStatus());
                drawText(contentStream, 50, 545, "Approved Amount: INR " + (claim.getApprovedAmount() != null ? claim.getApprovedAmount() : 0.0));
                
                if (claim.getInternalRemarks() != null && !claim.getInternalRemarks().isEmpty()) {
                    drawSectionTitle(contentStream, 50, 510, "Claims Officer Remarks");
                    drawText(contentStream, 50, 490, claim.getInternalRemarks());
                }

                if (claim.getResolvedAt() != null) {
                    drawText(contentStream, 50, 450, "Settled On: " + claim.getResolvedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                }

                drawFooter(contentStream);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void drawHeader(PDPageContentStream contentStream, String title) throws IOException {
        // Logo Bar
        contentStream.setNonStrokingColor(new Color(140, 29, 64)); // EventGuard Maroon
        contentStream.addRect(0, 740, 612, 60);
        contentStream.fill();

        contentStream.beginText();
        contentStream.setNonStrokingColor(Color.WHITE);
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
        contentStream.newLineAtOffset(50, 765);
        contentStream.showText("EventGuard");
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(50, 750);
        contentStream.showText("Smart Event Insurance Platform");
        contentStream.endText();

        contentStream.beginText();
        contentStream.setNonStrokingColor(Color.BLACK);
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
        contentStream.newLineAtOffset(50, 710);
        contentStream.showText(title);
        contentStream.endText();

        contentStream.setStrokingColor(Color.LIGHT_GRAY);
        contentStream.moveTo(50, 700);
        contentStream.lineTo(562, 700);
        contentStream.stroke();
    }

    private void drawSectionTitle(PDPageContentStream contentStream, float x, float y, String title) throws IOException {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
        contentStream.setNonStrokingColor(new Color(140, 29, 64));
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(title.toUpperCase());
        contentStream.endText();
        contentStream.setNonStrokingColor(Color.BLACK);
    }

    private void drawFooter(PDPageContentStream contentStream) throws IOException {
        contentStream.setStrokingColor(Color.LIGHT_GRAY);
        contentStream.moveTo(50, 80);
        contentStream.lineTo(562, 80);
        contentStream.stroke();

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        contentStream.setNonStrokingColor(Color.GRAY);
        contentStream.newLineAtOffset(50, 60);
        contentStream.showText("Contact eventguard@gmail.com for any queries.");
        contentStream.endText();

        contentStream.beginText();
        contentStream.newLineAtOffset(50, 45);
        contentStream.showText("This is an electronically generated document. Powered by EventGuard Technologies.");
        contentStream.endText();
    }

    private void drawText(PDPageContentStream contentStream, float x, float y, String text) throws IOException {
        if (text == null) text = "N/A";
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
}
