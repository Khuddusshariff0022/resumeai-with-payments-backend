package com.resumeai.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Razorpay subscription ID
    @Column(unique = true)
    private String razorpaySubscriptionId;

    // Razorpay plan ID (created in Razorpay dashboard)
    private String razorpayPlanId;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.CREATED;

    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum SubscriptionStatus {
        CREATED, ACTIVE, CANCELLED, EXPIRED
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE
            && expiresAt != null
            && expiresAt.isAfter(LocalDateTime.now());
    }
}
