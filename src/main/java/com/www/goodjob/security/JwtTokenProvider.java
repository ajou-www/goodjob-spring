package com.www.goodjob.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // 유효기간 상수 정의
    private final long ACCESS_TOKEN_VALID_TIME = 1000L * 60 * 60 * 24 * 30; // 30일
    private final long REFRESH_TOKEN_VALID_TIME = 1000L * 60 * 60 * 24 * 14; // 14일 (쿠키 TTL과 통일)

    private final String secretKey;

    public JwtTokenProvider(@Value("${jwt.secretKey:my-secret-key}") String secretKey) {
        this.secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public String generateAccessToken(String email) {
        return buildToken(email, null, ACCESS_TOKEN_VALID_TIME);
    }

    public String generateRefreshToken(String email) {
        return generateRefreshToken(email, false);
    }

    public String generateRefreshToken(String email, boolean isFirstLogin) {
        return buildToken(email, isFirstLogin, REFRESH_TOKEN_VALID_TIME);
    }

    private String buildToken(String email, Boolean firstLogin, long validityMs) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + validityMs))
                .signWith(SignatureAlgorithm.HS256, secretKey);

        if (firstLogin != null) {
            builder.claim("firstLogin", firstLogin);
        }

        return builder.compact();
    }

    public Boolean getFirstLoginClaim(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody();

        Object value = claims.get("firstLogin");
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
    }

    public boolean validateToken(String token) {
        return validateTokenDetailed(token) == TokenValidationResult.VALID;
    }

    public TokenValidationResult validateTokenDetailed(String token) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return TokenValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return TokenValidationResult.INVALID;
        }
    }

    public String getEmail(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public enum TokenValidationResult {
        VALID, EXPIRED, INVALID
    }
}
