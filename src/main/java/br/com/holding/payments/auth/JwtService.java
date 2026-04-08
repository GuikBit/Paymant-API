package br.com.holding.payments.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateToken(UserEntity user) {
        return buildToken(user, expirationMs);
    }

    public String generateRefreshToken(UserEntity user) {
        return buildToken(user, refreshExpirationMs);
    }

    private String buildToken(UserEntity user, long expiration) {
        Date now = new Date();
        Set<Role> roles = user.getRoleSet();
        String rolesStr = roles.stream().map(Role::name).collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("company_id", user.getCompany().getId())
                .claim("user_id", user.getId())
                .claim("roles", rolesStr)
                .claim("name", user.getName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public Long getCompanyIdFromToken(String token) {
        return parseToken(token).get("company_id", Long.class);
    }
}
