package com.fannet.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt (a supported legacy format) truncates at 72 bytes, so cap there; ASCII-only keeps chars = bytes.
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "3-20 letters, digits or underscores") String handle,
        @Size(max = 50) String displayName,
        @NotNull @Past LocalDate dateOfBirth) {
}
