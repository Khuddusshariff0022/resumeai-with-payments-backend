package com.resumeai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

    public String generateResume(String name, String role, String experience,
                                  String skills, String education, String achievements,
                                  String tone, String jobDesc, String company) {
        boolean hasJd = jobDesc != null && !jobDesc.isBlank();
        String jdSection = hasJd
            ? "\nTARGET JOB DESCRIPTION:\nCompany: " + (company != null && !company.isBlank() ? company : "the company")
              + "\nDescription: " + jobDesc
              + "\nInstructions: Extract top 10 keywords, mirror JD language, tailor every section.\n"
            : "";
        String prompt =
            "Write a professional " + tone.toLowerCase() + " resume for:\n"
            + "Name: " + name + "\nTarget Role: " + role + "\nExperience: " + experience + "\n"
            + "Skills: " + skills + "\nEducation: " + education + "\nAchievements: " + achievements + "\n"
            + jdSection + "\nFormat: Summary, Experience, Skills, Education sections.\n"
            + "ATS rules: plain text only, no tables or columns. One page content.";
        return generate(prompt);
    }

    public String generateCoverLetter(String name, String role, String experience,
                                       String skills, String jobTitle, String company,
                                       String jobDesc, String tone) {
        boolean hasJd = jobDesc != null && !jobDesc.isBlank();
        String jdContext = hasJd ? "Job Description:\n" + jobDesc + "\n" : "";
        String prompt =
            "Write a compelling " + tone.toLowerCase() + " cover letter for:\n"
            + "Applicant: " + name + "\nBackground: " + role + " — " + experience + "\n"
            + "Skills: " + skills + "\nApplying for: " + jobTitle + " at " + company + "\n"
            + jdContext + "\n3 paragraphs: opening hook, value proposition, CTA. No filler.";
        return generate(prompt);
    }

    public String generateWithDocument(String base64Data, String mediaType, String prompt) {
        Map<String, Object> documentBlock = Map.of(
            "type", "document",
            "source", Map.of("type", "base64", "media_type", mediaType, "data", base64Data)
        );
        Map<String, Object> textBlock = Map.of("type", "text", "text", prompt);
        Map<String, Object> body = new HashMap<>();
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
