package com.resumeai.controller;

import com.resumeai.dto.ResumeDto;
import com.resumeai.model.User;
import com.resumeai.model.UserRepository;
import com.resumeai.service.ClaudeService;
import com.resumeai.service.PdfService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ClaudeService claudeService;
    private final PdfService pdfService;
    private final UserRepository userRepository;

    public ResumeController(ClaudeService claudeService,
                            PdfService pdfService,
                            UserRepository userRepository) {
        this.claudeService = claudeService;
        this.pdfService = pdfService;
        this.userRepository = userRepository;
    }

    // Generate resume/cover letter (requires credits)
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@Valid @RequestBody ResumeDto.GenerateRequest req,
                                       Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();

        int requiredCredits = req.getPlan().equalsIgnoreCase("BUNDLE") ? 2 : 1;
        if (user.getCredits() < requiredCredits) {
            return ResponseEntity.status(402)
                    .body("Insufficient credits. Please purchase a plan.");
        }

        user.setCredits(user.getCredits() - requiredCredits);
        userRepository.save(user);

        String resume = null;
        String coverLetter = null;
        String plan = req.getPlan().toUpperCase();

        if (plan.equals("RESUME") || plan.equals("BUNDLE")) {
            resume = claudeService.generateResume(
                req.getName(), req.getRole(), req.getExperience(),
                req.getSkills(), req.getEducation(), req.getAchievements(),
                req.getTone(), req.getJobDesc(), req.getCompany()   // pass JD for optimization
            );
        }

        if (plan.equals("COVER") || plan.equals("BUNDLE")) {
            coverLetter = claudeService.generateCoverLetter(
                req.getName(), req.getRole(), req.getExperience(),
                req.getSkills(), req.getJobTitle(), req.getCompany(),
                req.getJobDesc(), req.getTone()
            );
        }

        return ResponseEntity.ok(new ResumeDto.GenerateResponse(resume, coverLetter, user.getCredits()));
    }

    // Download resume as PDF — filename prefixed with company name if JD provided
    @PostMapping("/download/resume")
    public ResponseEntity<byte[]> downloadResume(@RequestBody ResumeDto.GenerateRequest req,
                                                  Authentication auth) {
        userRepository.findByEmail(auth.getName()).orElseThrow();

        String content = claudeService.generateResume(
            req.getName(), req.getRole(), req.getExperience(),
            req.getSkills(), req.getEducation(), req.getAchievements(),
            req.getTone(), req.getJobDesc(), req.getCompany()
        );

        byte[] pdf = pdfService.generateResumePdf(req.getName(), req.getRole(), content);

        String filename = buildFilename(req.getName(), req.getCompany(), "Resume");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Download cover letter as PDF
    @PostMapping("/download/cover")
    public ResponseEntity<byte[]> downloadCover(@RequestBody ResumeDto.GenerateRequest req,
                                                 Authentication auth) {
        userRepository.findByEmail(auth.getName()).orElseThrow();

        String content = claudeService.generateCoverLetter(
            req.getName(), req.getRole(), req.getExperience(),
            req.getSkills(), req.getJobTitle(), req.getCompany(),
            req.getJobDesc(), req.getTone()
        );

        byte[] pdf = pdfService.generateCoverLetterPdf(req.getName(), req.getCompany(), content);

        String filename = buildFilename(req.getName(), req.getCompany(), "CoverLetter");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // Get user credit balance
    @GetMapping("/credits")
    public ResponseEntity<?> getCredits(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.ok(java.util.Map.of("credits", user.getCredits()));
    }

    /**
     * Builds PDF filename.
     * With company:  Google_JaneDoe_Resume.pdf
     * Without:       JaneDoe_Resume.pdf
     */
    private String buildFilename(String name, String company, String type) {
        String safeName = (name != null ? name : "User").replace(" ", "");
        boolean hasCompany = company != null && !company.isBlank();
        String safeCompany = hasCompany ? company.trim().replace(" ", "") + "_" : "";
        return safeCompany + safeName + "_" + type + ".pdf";
    }
}
