package io.github.coldzero7362.pdfsign.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 요청자 계정. Google 계정 하나에 하나씩 만든다. */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "google_sub", nullable = false, unique = true, length = 64)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String googleSub, String email, String name, Instant now) {
        this.publicId = UUID.randomUUID();
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    /** 로그인할 때마다 Google 프로필의 최신 값으로 맞춘다. */
    public void recordLogin(String email, String name, Instant now) {
        this.email = email;
        this.name = name;
        this.lastLoginAt = now;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getGoogleSub() { return googleSub; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
