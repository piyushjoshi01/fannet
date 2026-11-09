package com.fannet.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code fannet.auth.*}. The minimum age defaults to the stricter EU value (decision 0005). */
@ConfigurationProperties("fannet.auth")
public record AuthProperties(@DefaultValue("16") int minAge) {
}
