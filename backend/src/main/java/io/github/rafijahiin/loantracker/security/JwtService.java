package io.github.rafijahiin.loantracker.security;

import io.github.rafijahiin.loantracker.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = props.expiryMinutes() * 60;
    }

    /** The partner id travels in the token so that scoping decisions do not
     *  need a database read on every request. It is signed, so a client cannot
     *  edit it to read another partner's portfolio. */
    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .claim("partnerId", user.getPartnerId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(key)
                .compact();
    }

    /** Returns the claims, or empty when the token is absent, expired, tampered
     *  with, or signed by another key. Callers treat all of those the same way:
     *  the request is simply unauthenticated. */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}
