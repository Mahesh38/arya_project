package com.smartgrievance.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class CurrentUser implements UserDetails, CredentialsContainer {
    private final long id;
    private final String email;
    private final String fullName;
    private final Role role;
    private final Long departmentId;
    private final String departmentName;
    private final boolean active;
    private final boolean mobileVerified;
    private final int version;
    private final String fingerprint;
    private String passwordHash;

    public CurrentUser(Account account) {
        id = account.id();
        email = account.email();
        fullName = account.fullName();
        role = account.role();
        departmentId = account.departmentId();
        departmentName = account.departmentName();
        active = account.active();
        mobileVerified = account.mobileVerified();
        version = account.version();
        passwordHash = account.passwordHash();
        fingerprint = fingerprint(passwordHash);
    }

    public static String fingerprint(String hash) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public long id() { return id; }
    public String fullName() { return fullName; }
    public Role role() { return role; }
    public Long departmentId() { return departmentId; }
    public String departmentName() { return departmentName; }
    public boolean mobileVerified() { return mobileVerified; }
    public int version() { return version; }
    public String fingerprint() { return fingerprint; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }
    @Override
    public String getUsername() { return email; }
    @Override
    public boolean isEnabled() { return active; }
    @Override
    public void eraseCredentials() { passwordHash = null; }
    @Override
    public String toString() { return "CurrentUser[id=" + id + ", role=" + role + "]"; }
}
