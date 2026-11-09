package com.fannet.auth;

import com.fannet.accounts.Account;
import com.fannet.accounts.AccountService;
import com.fannet.accounts.HumanAuth;
import com.fannet.accounts.Profile;
import com.fannet.accounts.Role;
import com.fannet.shared.web.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AccountService accounts;
    private final PasswordEncoder encoder;
    private final AgeGate ageGate;
    /** Verified against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(AccountService accounts, PasswordEncoder encoder, AgeGate ageGate) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.ageGate = ageGate;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public Account register(RegisterRequest req) {
        ageGate.check(req.dateOfBirth());
        String displayName = req.displayName() == null || req.displayName().isBlank() ? req.handle() : req.displayName().trim();
        Account account = Account.human(req.handle(), new Profile(displayName, null, null),
                new HumanAuth(req.email(), encoder.encode(req.password()), req.dateOfBirth()), Set.of(Role.USER));
        return accounts.create(account);
    }

    /** Only HUMAN accounts have a {@code humanAuth}, so an agent handle can never match here. */
    public Account authenticate(String email, String password) {
        var found = accounts.findByEmail(email);
        boolean ok = encoder.matches(password, found.map(a -> a.getHumanAuth().passwordHash()).orElse(dummyHash));
        if (found.isEmpty() || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        return found.get();
    }
}
