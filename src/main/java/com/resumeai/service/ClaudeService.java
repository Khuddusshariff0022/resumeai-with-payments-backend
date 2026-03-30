package com.resumeai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class ClaudeService {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.model}")
    private String model;

    private final WebClient webClient;

    public ClaudeService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generate(String prompt) {
        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 1500,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Claude API returned null");

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) throw new RuntimeException("Empty Claude response");

        return (String) content.get(0).get("text");
    }

    // jobDesc and company are optional — if provided, resume is JD-optimized
    public String generateResume(String name, String role, String experience,
                                  String skills, String education, String achievements,
                                  String tone, String jobDesc, String company) {

        boolean hasJd = jobDesc != null && !jobDesc.isBlank();

        String jdSection = hasJd
            ? "\nTARGET JOB DESCRIPTION (optimize specifically for this role):\n"
                + "Company: " + (company != null && !company.isBlank() ? company : "the company") + "\n"
                + "Description: " + jobDesc + "\n\n"
                + "Instructions:\n"
                + "- Extract the top 10 keywords and required skills from this job description\n"
                + "- Naturally weave those exact keywords into the resume content\n"
                + "- Mirror the language and terminology used in the JD\n"
                + "- Prioritize experiences and skills most relevant to this specific role\n"
                + "- The summary must directly address what this company is looking for\n"
            : "";

        String prompt =
            "Write a professional " + tone.toLowerCase() + " resume for:\n"
            + "Name: " + name + "\n"
            + "Target Role: " + role + "\n"
            + "Experience: " + experience + "\n"
            + "Skills: " + skills + "\n"
            + "Education: " + education + "\n"
            + "Key Achievements: " + achievements + "\n"
            + jdSection + "\n"
            + "Format with clear sections: Summary, Experience, Skills, Education.\n"
            + "Use strong action verbs and quantifiable impact in every bullet point.\n"
            + "ATS rules: no tables, no columns, no graphics — clean plain text only.\n"
            + "Keep it concise and powerful — one page worth of content.";

        return generate(prompt);
    }

    public String generateCoverLetter(String name, String role, String experience,
                                       String skills, String jobTitle, String company,
                                       String jobDesc, String tone) {

        boolean hasJd = jobDesc != null && !jobDesc.isBlank();
        String jdContext = hasJd ? "Job Description to tailor for:\n" + jobDesc + "\n" : "";

        String prompt =
            "Write a compelling " + tone.toLowerCase() + " cover letter for:\n"
            + "Applicant: " + name + "\n"
            + "Background: " + role + " with experience in " + experience + "\n"
            + "Skills: " + skills + "\n"
            + "Applying for: " + jobTitle + " at " + company + "\n"
            + jdContext + "\n"
            + "Structure: 3 concise paragraphs:\n"
            + "1. Strong opening hook that references the company and role specifically\n"
            + "2. Specific value proposition matching the JD requirements\n"
            + "3. Confident call to action\n"
            + "Make it personal, specific, and non-generic. No filler phrases.";

        return generate(prompt);
    }
}

    // ── PDF document support (for resume parsing) ────────────────────────────
    public String generateWithDocument(String base64Data, String mediaType, String prompt) {
        var documentBlock = Map.of(
            "type", "document",
            "source", Map.of("type", "base64", "media_type", mediaType, "data", base64Data)
        );
        var textBlock = Map.of("type", "text", "text", prompt);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("max_tokens", 800);
        body.put("messages", List.of(
            Map.of("role", "user", "content", List.of(documentBlock, textBlock))
        ));

        Map response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) throw new RuntimeException("Claude API returned null");
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) throw new RuntimeException("Empty Claude response");
        return (String) content.get(0).get("text");
    }
}
