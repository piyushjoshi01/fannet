package com.fannet.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fannet.support.AbstractMongoIntegrationTest;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

class AccountRepositoryTest extends AbstractMongoIntegrationTest {

    @Autowired
    AccountRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private static Account human(String handle, String email) {
        return Account.human(handle, new Profile("Fan", null, null),
                new HumanAuth(email, "hash", LocalDate.of(1990, 1, 1)), Set.of(Role.USER));
    }

    private static Account agent(String handle) {
        return Account.agent(handle, new Profile("Agent", null, null),
                new AgentConfig("football", "Knowledgeable pundit", "warm"));
    }

    @Test
    void savesAndLoadsBothTypes() {
        repo.save(human("Alice", "Alice@Example.com"));
        repo.save(agent("footballagent"));

        Account alice = repo.findByHandle("alice").orElseThrow();
        assertThat(alice.getType()).isEqualTo(AccountType.HUMAN);
        assertThat(alice.getHumanAuth().email()).isEqualTo("alice@example.com");
        assertThat(alice.getAgentConfig()).isNull();
        assertThat(repo.findByHumanAuthEmail("alice@example.com")).isPresent();

        Account bot = repo.findByHandle("footballagent").orElseThrow();
        assertThat(bot.getType()).isEqualTo(AccountType.AGENT);
        assertThat(bot.getHumanAuth()).isNull();
        assertThat(bot.getRoles()).containsExactly(Role.SYSTEM);
    }

    @Test
    void duplicateHandleIsRejectedCaseInsensitively() {
        repo.save(human("alice", "a@example.com"));
        assertThatThrownBy(() -> repo.save(human("ALICE", "b@example.com")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void duplicateEmailIsRejected() {
        repo.save(human("alice", "a@example.com"));
        assertThatThrownBy(() -> repo.save(human("bob", "A@example.com")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void agentsWithoutEmailDoNotCollide() {
        repo.save(agent("arsenal"));
        repo.save(agent("chelsea"));
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    void factoriesRejectInconsistentAccounts() {
        assertThatThrownBy(() -> Account.human("x", null, null, Set.of(Role.USER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.human("x", null,
                new HumanAuth("x@e.com", "h", null), Set.of(Role.SYSTEM)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.agent("x", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
