package com.openrangelabs.donpetre.ingestion.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a knowledge item extracted from external sources
 */
public class KnowledgeItem {

    private UUID id;
    private String title;
    private String content;
    private String sourceType;
    private String sourceReference;
    private String author;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, Object> metadata;
    private String extractedText;
    private Double relevanceScore;

    public KnowledgeItem() {
        this.id = UUID.randomUUID();
        this.metadata = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public KnowledgeItem(String title, String content, String sourceType, String sourceReference) {
        this();
        this.title = title;
        this.content = content;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
    }

    // Builder pattern for complex construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final KnowledgeItem item = new KnowledgeItem();

        public Builder title(String title) {
            item.title = title;
            return this;
        }

        public Builder content(String content) {
            item.content = content;
            return this;
        }

        public Builder sourceType(String sourceType) {
            item.sourceType = sourceType;
            return this;
        }

        public Builder sourceReference(String sourceReference) {
            item.sourceReference = sourceReference;
            return this;
        }

        public Builder author(String author) {
            item.author = author;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            item.createdAt = createdAt;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            item.metadata.put(key, value);
            return this;
        }

        public Builder extractedText(String extractedText) {
            item.extractedText = extractedText;
            return this;
        }

        public Builder relevanceScore(Double relevanceScore) {
            item.relevanceScore = relevanceScore;
            return this;
        }

        public KnowledgeItem build() {
            return item;
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public Double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Double relevanceScore) { this.relevanceScore = relevanceScore; }

    @Override
    public String toString() {
        return "KnowledgeItem{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", sourceType='" + sourceType + '\'' +
                ", sourceReference='" + sourceReference + '\'' +
                ", author='" + author + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}