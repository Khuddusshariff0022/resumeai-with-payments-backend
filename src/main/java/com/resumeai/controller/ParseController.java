package com.resumeai.controller;

import com.resumeai.service.ClaudeService;
import com.resumeai.service.ResumeParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ParseController {

    private final ResumeParserService parserService;

    public ParseController(ResumeParserService parserService) {
        this.parserService = parserService;
    }

    /**
     * POST /api/resume/parse
     * Accepts a PDF, DOCX, or TXT file.
     * Returns a JSON object with extracted fields:
     *   name, role, experience, skills, education, achievements
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parseResume(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded");
        }

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        if (!originalName.endsWith(".pdf") &&
            !originalName.endsWith(".docx") &&
            !originalName.endsWith(".doc") &&
            !originalName.endsWith(".txt")) {
            return ResponseEntity.badRequest().body("Unsupported file type. Use PDF, DOCX, or TXT.");
        }

        try {
            Map<String, String> parsed = parserService.parse(file);
            return ResponseEntity.ok(parsed);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to parse resume: " + e.getMessage());
        }
    }
}
