package com.fannet.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fannet.shared.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AgeGateTest {

    private final AgeGate gate = new AgeGate(new AuthProperties(16),
            Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void rejectsTheDayBeforeTheSixteenthBirthday() {
        assertThatThrownBy(() -> gate.check(LocalDate.of(2010, 6, 16)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("UNDERAGE");
    }

    @Test
    void acceptsOnTheSixteenthBirthday() {
        assertThatCode(() -> gate.check(LocalDate.of(2010, 6, 15))).doesNotThrowAnyException();
    }

    @Test
    void acceptsAdults() {
        assertThatCode(() -> gate.check(LocalDate.of(1990, 1, 1))).doesNotThrowAnyException();
    }
}
