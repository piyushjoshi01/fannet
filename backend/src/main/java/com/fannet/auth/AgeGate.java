package com.fannet.auth;

import com.fannet.shared.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AgeGate {

    private final AuthProperties props;
    private final Clock clock;

    public AgeGate(AuthProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Whole years completed on the Nth birthday count: someone born 2010-06-15 is 16 on 2026-06-15. */
    public void check(LocalDate dateOfBirth) {
        if (Period.between(dateOfBirth, LocalDate.now(clock)).getYears() < props.minAge()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "UNDERAGE",
                    "You must be at least " + props.minAge() + " years old to register");
        }
    }
}
