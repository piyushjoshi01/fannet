package com.fannet.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AccountSummary register(@Valid @RequestBody RegisterRequest req) {
        return AccountSummary.of(auth.register(req));
    }

    /** Returns the account only; task 1.7 replaces this with access and refresh tokens. */
    @PostMapping("/login")
    AccountSummary login(@Valid @RequestBody LoginRequest req) {
        return AccountSummary.of(auth.authenticate(req.email(), req.password()));
    }
}
