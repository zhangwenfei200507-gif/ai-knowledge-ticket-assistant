package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeDocumentMapper;
import com.zwf.aisupport.security.CurrentUser;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadStoresDocumentMetadataAndChunks() {
        authenticateAsUser();
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(42L);
            return null;
        }).when(knowledgeDocumentMapper).insert(any(KnowledgeDocument.class));

        String content = "A".repeat(500) + "\n\n" + "B".repeat(500) + "\n\n" + "C".repeat(500);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis-guide.md",
                "text/markdown",
                content.getBytes(StandardCharsets.UTF_8)
        );

        KnowledgeDocumentVO result = documentService.upload(file, "ops");

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getOriginalFilename()).isEqualTo("redis-guide.md");
        assertThat(result.getCategory()).isEqualTo("ops");
        assertThat(result.getUploadedBy()).isEqualTo(7L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper).insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getOriginalFilename()).isEqualTo("redis-guide.md");
        assertThat(documentCaptor.getValue().getUploadedBy()).isEqualTo(7L);

        ArgumentCaptor<DocumentChunk> chunkCaptor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(documentChunkMapper, org.mockito.Mockito.times(3)).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues())
                .extracting(DocumentChunk::getDocumentId)
                .containsOnly(42L);
        assertThat(chunkCaptor.getAllValues())
                .extracting(DocumentChunk::getChunkIndex)
                .containsExactly(0, 1, 2);
        assertThat(chunkCaptor.getAllValues())
                .extracting(chunk -> chunk.getContent().length())
                .allMatch(length -> length <= 800);
    }

    @Test
    void uploadRejectsUnsupportedFileType() {
        authenticateAsUser();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.exe",
                "application/octet-stream",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.upload(file, "ops"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only .txt, .md, .pdf and .docx document files are supported");

        verify(knowledgeDocumentMapper, never()).insert(any(KnowledgeDocument.class));
        verify(documentChunkMapper, never()).insert(any(DocumentChunk.class));
    }

    @Test
    void uploadExtractsDocxTextIntoChunks() throws Exception {
        authenticateAsUser();
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(88L);
            return null;
        }).when(knowledgeDocumentMapper).insert(any(KnowledgeDocument.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "incident-runbook.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocxBytes("Redis token validation runbook", "Check Redis health and JWT refresh flow.")
        );

        KnowledgeDocumentVO result = documentService.upload(file, "auth");

        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getOriginalFilename()).isEqualTo("incident-runbook.docx");

        ArgumentCaptor<DocumentChunk> chunkCaptor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(documentChunkMapper).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue().getContent())
                .contains("Redis token validation runbook")
                .contains("Check Redis health and JWT refresh flow.");
    }

    private byte[] createDocxBytes(String... paragraphs) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("word/document.xml"));
            StringBuilder documentXml = new StringBuilder("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                    """);
            for (String paragraph : paragraphs) {
                documentXml.append("<w:p><w:r><w:t>")
                        .append(paragraph)
                        .append("</w:t></w:r></w:p>");
            }
            documentXml.append("</w:body></w:document>");
            zipOutputStream.write(documentXml.toString().getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }

    private void authenticateAsUser() {
        CurrentUser currentUser = new CurrentUser(7L, "alice", "USER");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
