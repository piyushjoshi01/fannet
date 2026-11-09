package com.fannet.accounts;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One abstraction for humans and agents (decision 0004). Create instances through
 * {@link #human} / {@link #agent}, which enforce the type/sub-document invariant.
 */
@Document("accounts")
public class Account {

    @Id
    private final String id;
    private final AccountType type;
    private final String handle;
    private final Profile profile;
    private final Set<Role> roles;
    private final HumanAuth humanAuth;
    private final AgentConfig agentConfig;
    private final Instant createdAt;

    @PersistenceCreator
    Account(String id, AccountType type, String handle, Profile profile, Set<Role> roles,
            HumanAuth humanAuth, AgentConfig agentConfig, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.handle = handle;
        this.profile = profile;
        this.roles = roles;
        this.humanAuth = humanAuth;
        this.agentConfig = agentConfig;
        this.createdAt = createdAt;
    }

    public static Account human(String handle, Profile profile, HumanAuth auth, Set<Role> roles) {
        if (auth == null || auth.email() == null || auth.email().isBlank()) {
            throw new IllegalArgumentException("A HUMAN account requires humanAuth with an email");
        }
        if (roles == null || roles.isEmpty() || roles.contains(Role.SYSTEM)) {
            throw new IllegalArgumentException("A HUMAN account needs USER and/or ADMIN roles, never SYSTEM");
        }
        HumanAuth normalized = new HumanAuth(normalize(auth.email()), auth.passwordHash(), auth.dateOfBirth());
        return new Account(null, AccountType.HUMAN, normalize(handle), profile, Set.copyOf(roles),
                normalized, null, Instant.now());
    }

    public static Account agent(String handle, Profile profile, AgentConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("An AGENT account requires agentConfig");
        }
        return new Account(null, AccountType.AGENT, normalize(handle), profile, Set.of(Role.SYSTEM),
                null, config, Instant.now());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("handle/email must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public String getId() { return id; }
    public AccountType getType() { return type; }
    public String getHandle() { return handle; }
    public Profile getProfile() { return profile; }
    public Set<Role> getRoles() { return roles; }
    public HumanAuth getHumanAuth() { return humanAuth; }
    public AgentConfig getAgentConfig() { return agentConfig; }
    public Instant getCreatedAt() { return createdAt; }
}
