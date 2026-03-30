package com.resumeai.dto;

import lombok.Data;

public class PaymentDto {

    @Data
    public static class CreateOrderRequest {
        private String plan; // STARTER or PRO
    }

    @Data
    public static class CreateOrderResponse {
        private String razorpayOrderId;
        private int amount;
        private String currency;
        private String plan;
        private String keyId;
        private int creditsIncluded;
        private String description;

        public CreateOrderResponse(String razorpayOrderId, int amount, String currency,
                                   String plan, String keyId, int creditsIncluded, String description) {
            this.razorpayOrderId = razorpayOrderId;
            this.amount = amount;
            this.currency = currency;
            this.plan = plan;
            this.keyId = keyId;
            this.creditsIncluded = creditsIncluded;
            this.description = description;
        }
    }

    @Data
    public static class VerifyPaymentRequest {
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
    }

    @Data
    public static class VerifyPaymentResponse {
        private boolean success;
        private int creditsAdded;
        private int totalCredits;
        private boolean subscribed;
        private String message;

        public VerifyPaymentResponse(boolean success, int creditsAdded,
                                     int totalCredits, boolean subscribed, String message) {
            this.success = success;
            this.creditsAdded = creditsAdded;
            this.totalCredits = totalCredits;
            this.subscribed = subscribed;
            this.message = message;
        }
    }

    @Data
    public static class CreateSubscriptionResponse {
        private String razorpaySubscriptionId;
        private String razorpayPlanId;
        private String keyId;
        private String shortUrl;

        public CreateSubscriptionResponse(String razorpaySubscriptionId, String razorpayPlanId,
                                          String keyId, String shortUrl) {
            this.razorpaySubscriptionId = razorpaySubscriptionId;
            this.razorpayPlanId = razorpayPlanId;
            this.keyId = keyId;
            this.shortUrl = shortUrl;
        }
    }

    @Data
    public static class VerifySubscriptionRequest {
        private String razorpaySubscriptionId;
        private String razorpayPaymentId;
        private String razorpaySignature;
    }

    @Data
    public static class BillingStatus {
        private int credits;
        private boolean subscribed;
        private String plan;
        private String nextRenewal;

        public BillingStatus(int credits, boolean subscribed, String plan, String nextRenewal) {
            this.credits = credits;
            this.subscribed = subscribed;
            this.plan = plan;
            this.nextRenewal = nextRenewal;
        }
    }
}
