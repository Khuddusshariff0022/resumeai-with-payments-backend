package com.resumeai.controller;

import com.resumeai.dto.PaymentDto;
import com.resumeai.model.User;
import com.resumeai.model.UserRepository;
import com.resumeai.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService  paymentService;
    private final UserRepository  userRepository;

    public PaymentController(PaymentService paymentService, UserRepository userRepository) {
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    // ── One-time credit pack ──────────────────────────────────────────────────

    /** Step 1: Create Razorpay order for credit pack */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody PaymentDto.CreateOrderRequest req,
                                          Authentication auth) {
        User user = getUser(auth);
        try {
            return ResponseEntity.ok(paymentService.createCreditOrder(user, req.getPlan()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to create order: " + e.getMessage());
        }
    }

    /** Step 2: Verify payment after Razorpay callback */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentDto.VerifyPaymentRequest req,
                                            Authentication auth) {
        User user = getUser(auth);
        PaymentDto.VerifyPaymentResponse res = paymentService.verifyCreditPayment(user, req);
        return res.isSuccess()
            ? ResponseEntity.ok(res)
            : ResponseEntity.status(400).body(res);
    }

    // ── Unlimited subscription ────────────────────────────────────────────────

    /** Step 1: Create Razorpay subscription */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(Authentication auth) {
        User user = getUser(auth);
        try {
            return ResponseEntity.ok(paymentService.createSubscription(user));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to create subscription: " + e.getMessage());
        }
    }

    /** Step 2: Verify subscription payment */
    @PostMapping("/verify-subscription")
    public ResponseEntity<?> verifySubscription(
            @RequestBody PaymentDto.VerifySubscriptionRequest req,
            Authentication auth) {
        User user = getUser(auth);
        PaymentDto.VerifyPaymentResponse res = paymentService.verifySubscription(user, req);
        return res.isSuccess()
            ? ResponseEntity.ok(res)
            : ResponseEntity.status(400).body(res);
    }

    // ── Webhook (Razorpay → backend, no auth) ─────────────────────────────────
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String sig) {
        paymentService.handleWebhook(payload, sig);
        return ResponseEntity.ok("OK");
    }

    // ── Billing status ────────────────────────────────────────────────────────
    @GetMapping("/billing")
    public ResponseEntity<?> billing(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(paymentService.getBillingStatus(user));
    }

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}
