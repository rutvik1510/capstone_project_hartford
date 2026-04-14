package org.hartford.eventguard.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.hartford.eventguard.entity.Claim;
import org.hartford.eventguard.entity.Event;
import org.hartford.eventguard.entity.User;
import org.hartford.eventguard.repo.ClaimsRepository;
import org.hartford.eventguard.repo.EventRepository;
import org.hartford.eventguard.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class RAGService {

    private static final Logger logger = LoggerFactory.getLogger(RAGService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ClaimsRepository claimsRepository;

    public RAGService(UserRepository userRepository, EventRepository eventRepository, ClaimsRepository claimsRepository) {
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.claimsRepository = claimsRepository;
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing DATABASE-AWARE RAG...");
        indexSystemDocuments();
        indexExistingUploads();
    }

    private void indexSystemDocuments() {
        try {
            File docsDir = new File("../Documents");
            if (!docsDir.exists()) return;
            
            ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
            File[] files = docsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (files == null) return;

            for (File file : files) {
                String name = file.getName().toUpperCase();
                if (name.contains("SRS") || name.contains("UML") || name.contains("DIAGRAM") || name.contains("TEST")) continue;

                Document document = FileSystemDocumentLoader.loadDocument(file.toPath(), parser);
                document.metadata().add("visibility", "SYSTEM_PUBLIC");
                document.metadata().add("source", file.getName());
                
                DocumentSplitter splitter = new DocumentByParagraphSplitter(500, 100);
                embeddingStore.addAll(embeddingModel.embedAll(splitter.split(document)).content(), splitter.split(document));
            }
        } catch (Exception e) {
            logger.error("System indexing failed: {}", e.getMessage());
        }
    }

    private void indexExistingUploads() {
        logger.info("Syncing existing uploads with database mapping...");
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        DocumentSplitter splitter = new DocumentByParagraphSplitter(500, 100);

        // 1. Sync Event Compliance Docs
        List<Event> events = eventRepository.findAll();
        for (Event e : events) {
            if (e.getSafetyComplianceDocPath() != null) {
                indexFileWithMetadata(e.getSafetyComplianceDocPath(), e.getUser().getUserId(), 
                    Map.of("eventName", e.getEventName(), "type", "COMPLIANCE", "eventId", e.getEventId()), parser, splitter);
            }
        }

        // 2. Sync Claim Evidence Docs
        List<Claim> claims = claimsRepository.findAll();
        for (Claim c : claims) {
            if (c.getEvidenceDocPath() != null) {
                indexFileWithMetadata(c.getEvidenceDocPath(), c.getPolicySubscription().getEvent().getUser().getUserId(), 
                    Map.of("eventName", c.getPolicySubscription().getEvent().getEventName(), "type", "EVIDENCE", "claimId", c.getClaimId()), parser, splitter);
            }
        }
    }

    private void indexFileWithMetadata(String fileName, Long userId, Map<String, Object> meta, ApachePdfBoxDocumentParser parser, DocumentSplitter splitter) {
        try {
            Path path = Paths.get("uploads", fileName);
            if (!java.nio.file.Files.exists(path)) return;

            Document document = FileSystemDocumentLoader.loadDocument(path, parser);
            document.metadata().add("visibility", "PRIVATE");
            document.metadata().add("ownerId", userId);
            meta.forEach((k, v) -> document.metadata().add(k, v));

            List<TextSegment> segments = splitter.split(document);
            embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
            logger.info("Linked & Indexed: {} -> Event: {}", fileName, meta.get("eventName"));
        } catch (Exception e) {
            logger.warn("Could not index file {}: {}", fileName, e.getMessage());
        }
    }

    public void indexUserDocument(Path path, Long userId, Map<String, Object> extraMetadata) {
        try {
            ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
            Document document = FileSystemDocumentLoader.loadDocument(path, parser);
            document.metadata().add("visibility", "PRIVATE");
            document.metadata().add("ownerId", userId);
            extraMetadata.forEach((k, v) -> document.metadata().add(k, v));

            DocumentSplitter splitter = new DocumentByParagraphSplitter(500, 100);
            embeddingStore.addAll(embeddingModel.embedAll(splitter.split(document)).content(), splitter.split(document));
        } catch (Exception e) {
            logger.error("Indexing failed: {}", e.getMessage());
        }
    }

    public String findRelevantContext(String query) {
        try {
            User user = getCurrentUser();
            if (user == null) return "";
            Filter filter = buildSecurityFilter(user);

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(query).content())
                    .maxResults(1) // ONLY fetch 1 chunk to save tokens
                    .minScore(0.3)
                    .filter(filter)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            return searchResult.matches().stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "";
        }
    }

    private Filter buildSecurityFilter(User user) {
        boolean isStaff = user.getRoles().stream()
                .map(r -> r.getRoleName())
                .anyMatch(r -> r.contains("ADMIN") || r.contains("UNDERWRITER") || r.contains("CLAIMS_OFFICER"));

        if (isStaff) return null;

        return Filter.or(
                metadataKey("visibility").isEqualTo("SYSTEM_PUBLIC"),
                Filter.and(
                        metadataKey("visibility").isEqualTo("PRIVATE"),
                        metadataKey("ownerId").isEqualTo(user.getUserId())
                )
        );
    }

    private User getCurrentUser() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
