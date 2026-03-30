package com.resumeai.service;

import com.resumeai.dto.PaymentDto;
import com.resumeai.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.plan.unlimited.id:plan_placeholder}")
    private String unlimitedPlanId;

    private final OrderRepository     orderRepository;
    private final UserRepository      userRepository;
    private final SubscriptionRepository subscriptionRepository;

    // ── Plan config ───────────────────────────────────────────────────────────
    // Prices in paise (1 INR = 100 paise)
    private static final Map<String, Integer> PLAN_PRICES = Map.of(
        "STARTER", 9900,   // ₹99
        "PRO",     19900   // ₹199
    );
    private static final Map<String, Integer> PLAN_CREDITS = Map.of(
        "STARTER", 3,
        "PRO",     10
    );
    private static final Map<String, String> PLAN_DESC = Map.of(
        "STARTER", "3 resume generations",
        "PRO",     "10 resume generations"
    );
    // Unlimited subscription: ₹399/month
    private static final int UNLIMITED_AMOUNT = 39900;

    public PaymentService(OrderRepository orderRepository,
                          UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository) {
        this.orderRepository      = orderRepository;
        this.userRepository       = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    // ── One-time credit order ─────────────────────────────────────────────────
    public PaymentDto.CreateOrderResponse createCreditOrder(User user, String plan) {
        plan = plan.toUpperCase();
        int amount  = PLAN_PRICES.getOrDefault(plan, 9900);
        int credits = PLAN_CREDITS.getOrDefault(plan, 3);
        String desc = PLAN_DESC.getOrDefault(plan, "Credits");

        String credentials = basicAuth();

        Map response = WebClient.builder()
                .baseUrl("https://api.razorpay.com")
                .build()
                .post()
                .uri("/v1/orders")
                .header("Authorization", "Basic " + credentials)
                .bodyValue(Map.of(
                    "amount",   amount,
                    "currency", "INR",
                    "receipt",  "rcpt_" + System.currentTimeMillis(),
                    "notes",    Map.of("plan", plan, "userId", user.getId().toString())
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String razorpayOrderId = (String) response.get("id");

        Order order = new Order();
        order.setUser(user);
        order.setRazorpayOrderId(razorpayOrderId);
        order.setPlan(plan);
        order.setAmount(amount);
        order.setCreditsGranted(credits);
        orderRepository.save(order);

        return new PaymentDto.CreateOrderResponse(
            razorpayOrderId, amount, "INR", plan, keyId, credits, desc);
    }

    // ── Verify one-time payment ───────────────────────────────────────────────
    public PaymentDto.VerifyPaymentResponse verifyCreditPayment(
            User user, PaymentDto.VerifyPaymentRequest req) {

        String payload = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        if (!verifyHmac(payload, req.getRazorpaySignature())) {
            return new PaymentDto.VerifyPaymentResponse(
                false, 0, user.getCredits(), user.isSubscribed(), "Invalid signature");
        }

        Order order = orderRepository.findByRazorpayOrderId(req.getRazorpayOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setRazorpayPaymentId(req.getRazorpayPaymentId());
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        user.setCredits(user.getCredits() + order.getCreditsGranted());
        userRepository.save(user);

        return new PaymentDto.VerifyPaymentResponse(
            true, order.getCreditsGranted(), user.getCredits(),
            user.isSubscribed(), "Credits added successfully!");
    }

    // ── Create Razorpay subscription ──────────────────────────────────────────
    public PaymentDto.CreateSubscriptionResponse createSubscription(User user) {
        String credentials = basicAuth();

        Map response = WebClient.builder()
                .baseUrl("https://api.razorpay.com")
                .build()
                .post()
                .uri("/v1/subscriptions")
                .header("Authorization", "Basic " + credentials)
                .bodyValue(Map.of(
                    "plan_id",        unlimitedPlanId,
                    "total_count",    12,          // 12 billing cycles
                    "quantity",       1,
                    "customer_notify", 1,
                    "notes",          Map.of("userId", user.getId().toString())
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String subId    = (String) response.get("id");
        String shortUrl = (String) response.getOrDefault("short_url", "");

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setRazorpaySubscriptionId(subId);
        sub.setRazorpayPlanId(unlimitedPlanId);
        sub.setStatus(Subscription.SubscriptionStatus.CREATED);
        subscriptionRepository.save(sub);

        return new PaymentDto.CreateSubscriptionResponse(subId, unlimitedPlanId, keyId, shortUrl);
    }

    // ── Verify subscription payment ───────────────────────────────────────────
    public PaymentDto.VerifyPaymentResponse verifySubscription(
            User user, PaymentDto.VerifySubscriptionRequest req) {

        // Razorpay subscription signature = HMAC(subscriptionId|paymentId)
        String payload = req.getRazorpayPaymentId() + "|" + req.getRazorpaySubscriptionId();
        if (!verifyHmac(payload, req.getRazorpaySignature())) {
            return new PaymentDto.VerifyPaymentResponse(
                false, 0, user.getCredits(), false, "Invalid signature");
        }

        Subscription sub = subscriptionRepository
                .findByRazorpaySubscriptionId(req.getRazorpaySubscriptionId())
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        sub.setStartedAt(LocalDateTime.now());
        sub.setExpiresAt(LocalDateTime.now().plusMonths(1));
        subscriptionRepository.save(sub);

        user.setSubscribed(true);
        userRepository.save(user);

        return new PaymentDto.VerifyPaymentResponse(
            true, 0, user.getCredits(), true, "Unlimited plan activated!");
    }

    // ── Razorpay webhook (subscription renewal / cancellation) ───────────────
    public void handleWebhook(String payload, String signature) {
        // Webhook signature = HMAC of raw body with webhook secret
        // In production set razorpay.webhook.secret in properties
        // For now just parse and handle events
        try {
            if (payload.contains("\"subscription.activated\"") ||
                payload.contains("\"subscription.charged\"")) {
                // Extract subscription ID from payload and extend expiry
                String subId = extractSubIdFromPayload(payload);
                if (subId != null) {
                    subscriptionRepository.findByRazorpaySubscriptionId(subId)
                        .ifPresent(sub -> {
                            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
                            sub.setExpiresAt(LocalDateTime.now().plusMonths(1));
                            subscriptionRepository.save(sub);
                            sub.getUser().setSubscribed(true);
                            userRepository.save(sub.getUser());
                        });
                }
            } else if (payload.contains("\"subscription.cancelled\"") ||
                       payload.contains("\"subscription.expired\"")) {
                String subId = extractSubIdFromPayload(payload);
                if (subId != null) {
                    subscriptionRepository.findByRazorpaySubscriptionId(subId)
                        .ifPresent(sub -> {
                            sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
                            subscriptionRepository.save(sub);
                            sub.getUser().setSubscribed(false);
                            userRepository.save(sub.getUser());
                        });
                }
            }
        } catch (Exception e) {
            System.err.println("Webhook processing error: " + e.getMessage());
        }
    }

    // ── Billing status ────────────────────────────────────────────────────────
    public PaymentDto.BillingStatus getBillingStatus(User user) {
        String plan = "FREE";
        String nextRenewal = null;

        if (user.isSubscribed()) {
            plan = "UNLIMITED";
            subscriptionRepository.findTopByUserOrderByCreatedAtDesc(user)
                .ifPresent(sub -> {});
            var sub = subscriptionRepository.findTopByUserOrderByCreatedAtDesc(user);
            if (sub.isPresent() && sub.get().getExpiresAt() != null) {
                nextRenewal = sub.get().getExpiresAt().toString();
            }
        } else if (user.getCredits() >= 10) {
            plan = "PRO";
        } else if (user.getCredits() > 0) {
            plan = "STARTER";
        }

        return new PaymentDto.BillingStatus(
            user.getCredits(), user.isSubscribed(), plan, nextRenewal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String basicAuth() {
        return Base64.getEncoder()
            .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    }

    private boolean verifyHmac(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractSubIdFromPayload(String payload) {
        // Simple extraction — in prod use Jackson ObjectMapper
        int idx = payload.indexOf("\"subscription_id\":\"");
        if (idx < 0) return null;
        int start = idx + 19;
        int end   = payload.indexOf("\"", start);
        return end > start ? payload.substring(start, end) : null;
    }
}
