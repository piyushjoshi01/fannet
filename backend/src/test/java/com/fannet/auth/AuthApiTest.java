package com.fannet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fannet.accounts.Account;
import com.fannet.accounts.AccountRepository;
import com.fannet.accounts.AgentConfig;
import com.fannet.accounts.Profile;
import com.fannet.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class AuthApiTest extends AbstractMongoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AccountRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private ResultActions register(String email, String handle, String dob) throws Exception {
        return post("/api/auth/register", """
                {"email":"%s","password":"correct-horse-battery","handle":"%s","dateOfBirth":"%s"}
                """.formatted(email, handle, dob));
    }

    private ResultActions login(String email, String password) throws Exception {
        return post("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private ResultActions post(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registerThenLogin() throws Exception {
        register("Alice@Example.com", "Alice", "1990-01-01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handle").value("alice"));

        Account stored = repo.findByHandle("alice").orElseThrow();
        assertThat(stored.getHumanAuth().passwordHash()).startsWith("{argon2}").doesNotContain("correct-horse");

        login("alice@example.com", "correct-horse-battery").andExpect(status().isOk());
    }

    @Test
    void underageIsForbiddenAndNothingIsStored() throws Exception {
        register("kid@example.com", "kid", java.time.LocalDate.now().minusYears(10).toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNDERAGE"));
        assertThat(repo.count()).isZero();
    }

    @Test
    void duplicateHandleAndEmailAreConflicts() throws Exception {
        register("a@example.com", "alice", "1990-01-01").andExpect(status().isCreated());
        register("b@example.com", "ALICE", "1990-01-01")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
        register("A@example.com", "bob", "1990-01-01")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        post("/api/auth/register", """
                {"email":"nope","password":"short","handle":"a b","dateOfBirth":"2999-01-01"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.handle").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        register("a@example.com", "alice", "1990-01-01").andExpect(status().isCreated());
        login("a@example.com", "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        login("ghost@example.com", "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void agentsCannotLogIn() throws Exception {
        repo.save(Account.agent("footballagent", new Profile("Football Agent", null, null),
                new AgentConfig("football", "Pundit", "warm")));
        login("footballagent", "anything").andExpect(status().isUnauthorized());
        login("footballagent@fannet.local", "anything").andExpect(status().isUnauthorized());
    }
}
