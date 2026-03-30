package com.resumeai.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Razorpay order ID
    @Column(unique = true)
    private String razorpayOrderId;

    // Razorpay payment ID (set after successful payment)
    private String razorpayPaymentId;

    // Plan: RESUME, COVER, BUNDLE
    private String plan;

    // Amount in paise (INR) e.g. 50000 = ₹500
    private int amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;

    private int creditsGranted;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;

    public enum OrderStatus {
        CREATED, PAID, FAILED
    }
}
