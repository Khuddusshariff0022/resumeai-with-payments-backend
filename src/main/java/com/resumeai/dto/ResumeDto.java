package com.resumeai.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

public class ResumeDto {

    @Data
    public static class GenerateRequest {
        @NotBlank private String name;
        @NotBlank private String role;
        @NotBlank private String experience;
        private String skills;
        private String education;
        private String achievements;
        private String jobTitle;
        private String company;
        private String jobDesc;
        private String tone = "Professional";
        // RESUME, COVER, BUNDLE
        private String plan = "BUNDLE";
    }

    @Data
    public static class GenerateResponse {
        private String resume;
        private String coverLetter;
        private int creditsRemaining;

        public GenerateResponse(String resume, String coverLetter, int creditsRemaining) {
            this.resume = resume;
            this.coverLetter = coverLetter;
            this.creditsRemaining = creditsRemaining;
        }
    }
}
