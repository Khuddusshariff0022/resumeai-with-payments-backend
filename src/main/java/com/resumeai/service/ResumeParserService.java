package com.resumeai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ResumeParserService {

    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTRACT_PROMPT = """
        Extract all information from this resume and return ONLY a valid JSON object with these exact keys:
        {
          "name": "full name",
          "role": "most recent job title or target role",
          "experience": "detailed summary of all work experience — include company names, roles, durations and key achievements",
          "skills": "comma-separated list of all technical and soft skills",
          "education": "education history with institution, degree, year",
          "achievements": "key achievements, awards, certifications, publications"
        }
        Return ONLY the JSON object. No explanation, no markdown, no backticks.
        """;

    public ResumeParserService(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    public Map<String, String> parse(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        String rawJson;

        if (filename.endsWith(".pdf")) {
            rawJson = parsePdf(file);
        } else if (filename.endsWith(".docx") || filename.endsWith(".doc")) {
            rawJson = parseDocx(file);
        } else {
            // TXT — read as plain text
            String text = new String(file.getBytes());
            rawJson = claudeService.generate(EXTRACT_PROMPT + "\n\nResume:\n" + text);
        }

        // Clean and parse JSON
        String clean = rawJson.replaceAll("```json|```", "").trim();
        return objectMapper.readValue(clean, Map.class);
    }

    // ── PDF: send as base64 document to Claude ───────────────────────────────
    private String parsePdf(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);

        // Build Claude API request with PDF document block
        return claudeService.generateWithDocument(base64, "application/pdf", EXTRACT_PROMPT);
    }

    // ── DOCX: extract text with Apache POI, then send to Claude ─────────────
    private String parseDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {

            String text = extractor.getText();
            return claudeService.generate(EXTRACT_PROMPT + "\n\nResume:\n" + text);
        }
    }
}
