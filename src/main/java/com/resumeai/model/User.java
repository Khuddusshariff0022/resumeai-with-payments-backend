package com.resumeai.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;

    // Pay-per-use credits (Starter / Pro plans)
    @Column(nullable = false)
    private int credits = 0;

    // Subscription flag — true when Unlimited plan is active
    @Column(nullable = false)
    private boolean subscribed = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    // Can generate if subscribed OR has credits
    public boolean canGenerate() {
        return subscribed || credits > 0;
    }
}
