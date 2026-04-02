package org.hartford.eventguard.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hartford.eventguard.entity.PolicySubscription;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DocumentAnalysisService {

    private final String uploadDir = "uploads";
    private final GroqService groqService;

    public DocumentAnalysisService(GroqService groqService) {
        this.groqService = groqService;
    }

    public String analyzeDocument(String fileName, String contextType) {
        if (fileName == null || fileName.isBlank()) {
            return "No document available for analysis.";
        }

        try {
            String extractedText = extractTextFromPdf(fileName);
            if (extractedText.isEmpty()) {
                return "The document appears to be empty or unreadable.";
            }

            // Limit text size for the model
            String truncatedText = extractedText.length() > 4000 ? extractedText.substring(0, 4000) : extractedText;

            String prompt = buildAnalysisPrompt(truncatedText, contextType);
            String rawResponse = groqService.generateContent(prompt);
            
            // Post-process to remove any remaining stars
            return rawResponse.replace("*", "");

        } catch (Exception e) {
            return "Error analyzing document: " + e.getMessage();
        }
    }

    public String analyzeSubscriptionWithContext(PolicySubscription sub) {
        String fileName = sub.getEvent().getSafetyComplianceDocPath();
        if (fileName == null || fileName.isBlank()) {
            return "No document available for analysis.";
        }

        try {
            String extractedText = extractTextFromPdf(fileName);
            String truncatedText = extractedText.length() > 4000 ? extractedText.substring(0, 4000) : extractedText;

            StringBuilder sb = new StringBuilder();
            sb.append("You are an expert insurance document auditor. Analyze the following extracted text from a PDF document in the context of an event insurance subscription.\n\n");
            
            sb.append("--- ORIGINAL RISK CALCULATED BY SYSTEM ---\n")
              .append("- Event Risk: ").append(sub.getEventRisk()).append("%\n")
              .append("- Weather Risk: ").append(sub.getWeatherRisk()).append("%\n")
              .append("- Total System Risk: ").append(sub.getTotalRisk()).append("%\n\n");

            sb.append("--- EVENT DETAILS ---\n")
              .append("- Event Name: ").append(sub.getEvent().getEventName()).append("\n")
              .append("- Type: ").append(sub.getEvent().getEventType()).append("\n")
              .append("- Attendees: ").append(sub.getEvent().getNumberOfAttendees()).append("\n\n");

            sb.append("EXTRACTED TEXT FROM PDF:\n").append(truncatedText).append("\n\n");
            
            sb.append("INSTRUCTIONS:\n")
              .append("- Provide a concise 'Smart Summary' (max 4-5 bullet points).\n")
              .append("- Highlight potential Red Flags or risks.\n")
              .append("- AI SUGGESTED RISK SCORE: Based on the document and the system risk, suggest a final risk score (0-20%). Explain if it should be higher or lower than the system risk.\n")
              .append("- At the end, provide a 'DECISION SUGGESTION' (APPROVE or REJECT) with a one-sentence reason.\n")
              .append("- IMPORTANT: DO NOT use asterisks (*) for bolding or bullet points. Use plain text and simple dashes (-) for lists.\n")
              .append("- Keep it professional and focus only on the most important parts for an insurance decision.");

            String rawResponse = groqService.generateContent(sb.toString());
            return rawResponse.replace("*", "");

        } catch (Exception e) {
            return "Error analyzing document: " + e.getMessage();
        }
    }

    private String extractTextFromPdf(String fileName) throws IOException {
        Path filePath = Paths.get(uploadDir).resolve(fileName);
        File file = filePath.toFile();

        if (!file.exists()) {
            throw new IOException("File not found: " + fileName);
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String buildAnalysisPrompt(String text, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert insurance document auditor. Analyze the following extracted text from a PDF document.\n\n");
        
        if ("SUBSCRIPTION".equalsIgnoreCase(type)) {
            sb.append("CONTEXT: Underwriting Review. Look for safety compliance, venue details, and infrastructure risks.\n");
        } else {
            sb.append("CONTEXT: Claims Verification. Look for evidence of loss, incident date consistency, and proof of damages.\n");
        }

        sb.append("\nEXTRACTED TEXT:\n").append(text).append("\n\n");
        
        sb.append("INSTRUCTIONS:\n")
          .append("- Provide a concise 'Smart Summary' (max 4-5 bullet points).\n")
          .append("- Highlight potential Red Flags or risks.\n")
          .append("- At the end, provide a 'DECISION SUGGESTION' (APPROVE or REJECT) with a one-sentence reason based on the backend data/extracted text.\n")
          .append("- IMPORTANT: DO NOT use asterisks (*) for bolding or bullet points. Use plain text and simple dashes (-) for lists.\n")
          .append("- Keep it professional and focus only on the most important parts for an insurance decision.");

        return sb.toString();
    }
}
