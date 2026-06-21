package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticChunkManagementStage {

    private static final int TARGET_CHUNK_TOKENS = 800;
    private static final int APPROX_CHARS_PER_TOKEN = 4;

    public List<String> splitDocument(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            throw new BusinessException("Document content cannot be blank");
        }

        List<String> chunks = new ArrayList<>();
        List<String> semanticUnits = splitIntoSemanticUnits(normalizedContent);

        StringBuilder currentChunk = new StringBuilder();
        for (String unit : semanticUnits) {
            String cleanedUnit = unit.trim();
            if (cleanedUnit.isBlank()) {
                continue;
            }

            if (estimateTokens(currentChunk.length() + cleanedUnit.length()) <= TARGET_CHUNK_TOKENS) {
                appendUnit(currentChunk, cleanedUnit);
                continue;
            }

            if (!currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }

            if (estimateTokens(cleanedUnit.length()) <= TARGET_CHUNK_TOKENS) {
                appendUnit(currentChunk, cleanedUnit);
                continue;
            }

            chunks.addAll(splitOversizedTopic(cleanedUnit));
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private List<String> splitIntoSemanticUnits(String content) {
        List<String> units = new ArrayList<>();
        String[] paragraphs = content.split("\\R\\s*\\R");
        StringBuilder currentUnit = new StringBuilder();

        for (String paragraph : paragraphs) {
            String cleanedParagraph = paragraph.trim();
            if (cleanedParagraph.isBlank()) {
                continue;
            }

            if (isHeading(cleanedParagraph) && !currentUnit.isEmpty()) {
                units.add(currentUnit.toString().trim());
                currentUnit.setLength(0);
            }
            appendUnit(currentUnit, cleanedParagraph);
        }

        if (!currentUnit.isEmpty()) {
            units.add(currentUnit.toString().trim());
        }
        return units.isEmpty() ? List.of(content) : units;
    }

    private List<String> splitOversizedTopic(String topic) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : topic.split("\\R\\s*\\R")) {
            String cleanedParagraph = paragraph.trim();
            if (cleanedParagraph.isBlank()) {
                continue;
            }

            if (estimateTokens(cleanedParagraph.length()) > TARGET_CHUNK_TOKENS) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
                chunks.addAll(splitOversizedParagraph(cleanedParagraph));
                continue;
            }

            if (estimateTokens(currentChunk.length() + cleanedParagraph.length()) > TARGET_CHUNK_TOKENS && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }
            appendUnit(currentChunk, cleanedParagraph);
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    private List<String> splitOversizedParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            String cleanedSentence = sentence.trim();
            if (cleanedSentence.isBlank()) {
                continue;
            }

            if (estimateTokens(currentChunk.length() + cleanedSentence.length()) > TARGET_CHUNK_TOKENS && !currentChunk.isEmpty()) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
            }
            appendUnit(currentChunk, cleanedSentence);
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks.isEmpty() ? List.of(paragraph) : chunks;
    }

    private boolean isHeading(String paragraph) {
        return paragraph.matches("^#{1,6}\\s+.+")
                || paragraph.matches("^[A-Z][A-Za-z0-9 /_-]{2,80}:?$");
    }

    private void appendUnit(StringBuilder builder, String unit) {
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(unit);
    }

    private int estimateTokens(int characterCount) {
        return (int) Math.ceil((double) Math.max(0, characterCount) / APPROX_CHARS_PER_TOKEN);
    }
}
