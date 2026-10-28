package com.zwf.aisupport.common.util;

import com.zwf.aisupport.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadedTextExtractorTest {

    @Test
    void rejectsTextThatExceedsTheSharedExtractionLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.txt", "text/plain", "x".repeat(1_000_001).getBytes()
        );

        assertThatThrownBy(() -> UploadedTextExtractor.extract(file, 2 * 1024 * 1024, "document"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1,000,000 character limit");
    }

    @Test
    void rejectsMalformedUtf8TextInsteadOfIndexingReplacementCharacters() {
        MockMultipartFile file = new MockMultipartFile("file", "invalid.txt", "text/plain", new byte[]{(byte) 0xc3, 0x28});

        assertThatThrownBy(() -> UploadedTextExtractor.extract(file, 1024, "document"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Document text must be valid UTF-8");
    }

    @Test
    void rejectsDocxWithOversizedExpandedMainDocumentXml() throws Exception {
        byte[] archive = zipMainDocumentXml("x".repeat(10 * 1024 * 1024 + 1));
        MockMultipartFile file = new MockMultipartFile(
                "file", "compressed.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", archive
        );

        assertThatThrownBy(() -> UploadedTextExtractor.extract(file, 2 * 1024 * 1024, "document"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("uncompressed size limit");
    }

    private byte[] zipMainDocumentXml(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<document>" + text + "</document>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
