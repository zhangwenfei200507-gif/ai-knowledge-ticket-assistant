package com.zwf.aisupport.common.util;

import com.zwf.aisupport.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UploadedTextExtractor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");
    private static final int MAX_EXTRACTED_TEXT_CHARS = 1_000_000;
    private static final int MAX_PDF_PAGES = 1_000;
    private static final int MAX_DOCX_ENTRIES = 2_000;
    private static final int MAX_DOCX_UNCOMPRESSED_BYTES = 20 * 1024 * 1024;
    private static final int MAX_DOCX_XML_BYTES = 10 * 1024 * 1024;

    private UploadedTextExtractor() {
    }

    public static ExtractedText extract(MultipartFile file, long maxFileSize, String fileLabel) {
        validate(file, maxFileSize, fileLabel);

        String filename = file.getOriginalFilename().trim();
        String extension = getExtension(filename);

        String content = switch (extension) {
            case "txt", "md" -> readPlainText(file, fileLabel);
            case "pdf" -> readPdf(file, fileLabel);
            case "docx" -> readDocx(file, fileLabel);
            default -> throw new BusinessException("Unsupported " + fileLabel + " file type");
        };

        String normalizedContent = normalizeContent(content);
        if (normalizedContent.isBlank()) {
            throw new BusinessException(capitalize(fileLabel) + " file content cannot be blank");
        }
        if (normalizedContent.length() > MAX_EXTRACTED_TEXT_CHARS) {
            throw new BusinessException(capitalize(fileLabel) + " extracted text exceeds the 1,000,000 character limit");
        }

        return new ExtractedText(filename, extension.toUpperCase(Locale.ROOT), normalizedContent);
    }

    public static String titleFromFilename(String filename, String fallbackTitle) {
        if (fallbackTitle != null && !fallbackTitle.isBlank()) {
            return fallbackTitle.trim();
        }

        String title = filename == null || filename.isBlank() ? "Uploaded Article" : filename.trim();
        int extensionIndex = title.lastIndexOf('.');
        if (extensionIndex > 0) {
            title = title.substring(0, extensionIndex);
        }
        return title;
    }

    private static void validate(MultipartFile file, long maxFileSize, String fileLabel) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(capitalize(fileLabel) + " file cannot be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new BusinessException(capitalize(fileLabel) + " file size cannot exceed " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(capitalize(fileLabel) + " filename cannot be blank");
        }

        String extension = getExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Only .txt, .md, .pdf and .docx " + fileLabel + " files are supported");
        }
    }

    private static String readPlainText(MultipartFile file, String fileLabel) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString()
                    .replaceFirst("^\\uFEFF", "");
        } catch (CharacterCodingException exception) {
            throw new BusinessException(capitalize(fileLabel) + " text must be valid UTF-8");
        } catch (Exception exception) {
            throw new BusinessException("Failed to read " + fileLabel + " text content");
        }
    }

    private static String readPdf(MultipartFile file, String fileLabel) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new BusinessException(capitalize(fileLabel) + " PDF exceeds the 1,000 page limit");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            LimitedTextWriter writer = new LimitedTextWriter(MAX_EXTRACTED_TEXT_CHARS);
            stripper.writeText(document, writer);
            return writer.toString();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Failed to extract text from " + fileLabel + " PDF");
        }
    }

    private static String readDocx(MultipartFile file, String fileLabel) {
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int entryCount = 0;
            int totalUncompressedBytes = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (++entryCount > MAX_DOCX_ENTRIES) {
                    throw new BusinessException("DOCX archive exceeds the entry limit");
                }
                boolean mainDocument = "word/document.xml".equals(entry.getName());
                int remainingBytes = MAX_DOCX_UNCOMPRESSED_BYTES - totalUncompressedBytes;
                int entryLimit = mainDocument ? Math.min(remainingBytes, MAX_DOCX_XML_BYTES) : remainingBytes;
                byte[] entryContent = readZipEntry(zipInputStream, entryLimit);
                totalUncompressedBytes += entryContent.length;
                if (mainDocument) {
                    return extractDocxDocumentText(new String(entryContent, StandardCharsets.UTF_8));
                }
            }
            throw new BusinessException("DOCX main document content was not found");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Failed to extract text from " + fileLabel + " DOCX");
        }
    }

    private static byte[] readZipEntry(ZipInputStream zipInputStream, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new BusinessException("DOCX archive exceeds the uncompressed size limit");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        int length;
        while ((length = zipInputStream.read(buffer)) != -1) {
            totalBytes += length;
            if (totalBytes > maxBytes) {
                throw new BusinessException("DOCX archive exceeds the uncompressed size limit");
            }
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private static String extractDocxDocumentText(String documentXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(documentXml)));

        NodeList textNodes = document.getElementsByTagNameNS("*", "t");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < textNodes.getLength(); index++) {
            String text = textNodes.item(index).getTextContent();
            if (text != null && !text.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(text.trim());
            }
        }
        return builder.toString();
    }

    private static String getExtension(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContent(String content) {
        return content == null ? "" : content.replace("\u0000", "").trim();
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Uploaded";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    public record ExtractedText(String filename, String fileType, String content) {
    }

    private static final class LimitedTextWriter extends Writer {

        private final int maxChars;
        private final StringBuilder content = new StringBuilder();

        private LimitedTextWriter(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            if (content.length() + length > maxChars) {
                throw new IOException("Extracted PDF text exceeds the configured limit");
            }
            content.append(chars, offset, length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return content.toString();
        }
    }
}
