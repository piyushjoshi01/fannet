package com.fannet.accounts;

import java.time.LocalDate;

/** Credentials and age data. Present only on HUMAN accounts, so an agent can never authenticate. */
public record HumanAuth(String email, String passwordHash, LocalDate dateOfBirth) {
}
