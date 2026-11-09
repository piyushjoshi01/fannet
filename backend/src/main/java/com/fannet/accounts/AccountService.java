package com.fannet.accounts;

import com.fannet.shared.web.ApiException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Public entry point of the accounts module; other modules must not touch the repository. */
@Service
public class AccountService {

    private final AccountRepository repo;

    public AccountService(AccountRepository repo) {
        this.repo = repo;
    }

    /** The unique indexes decide, so two concurrent registrations cannot both win. */
    public Account create(Account account) {
        try {
            return repo.insert(account);
        } catch (DuplicateKeyException e) {
            String msg = String.valueOf(e.getMessage());
            boolean email = msg.contains("uk_human_email") || msg.contains("humanAuth.email");
            throw new ApiException(HttpStatus.CONFLICT, email ? "EMAIL_TAKEN" : "HANDLE_TAKEN",
                    email ? "Email is already registered" : "Handle is already taken");
        }
    }

    public Optional<Account> findByEmail(String email) {
        return repo.findByHumanAuthEmail(email.trim().toLowerCase(Locale.ROOT));
    }
}
