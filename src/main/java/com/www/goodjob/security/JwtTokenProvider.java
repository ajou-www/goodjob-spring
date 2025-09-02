package com.www.goodjob.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    // 유효기간
    private final long ACCESS_TOKEN_VALID_TIME  = 1000L * 60 * 10;            // 10분
    private final long REFRESH_TOKEN_VALID_TIME = 1000L * 60 * 60 * 24 * 14;  // 14일

    private final String secretKey;

    public JwtTokenProvider(@Value("${jwt.secretKey:my-secret-key}") String secretKey) {
        this.secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    /* ===== Access Token ===== */
    public String generateAccessToken(String email) {
        return buildToken(email, null, null, ACCESS_TOKEN_VALID_TIME);
    }

    /* ===== Refresh Token (신규 jti 발급) ===== */
    public String generateRefreshToken(String email) {
        return generateRefreshToken(email, false);
    }

    public String generateRefreshToken(String email, boolean isFirstLogin) {
        String jti = UUID.randomUUID().toString(); // 기기/탭 고유 식별자
        return buildToken(email, jti, isFirstLogin, REFRESH_TOKEN_VALID_TIME);
    }

    /* ===== Refresh Token (기존 jti 재사용: 회전 시에 사용) ===== */
    public String generateRefreshTokenWithExistingJti(String email, String jti) {
        return buildToken(email, jti, null, REFRESH_TOKEN_VALID_TIME);
    }

    /* ===== 공통 빌더 ===== */
    private String buildToken(String email, String jti, Boolean firstLogin, long validityMs) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + validityMs))
                .signWith(SignatureAlgorithm.HS256, secretKey);

        if (jti != null) builder.claim("jti", jti);
        if (firstLogin != null) builder.claim("firstLogin", firstLogin);

        return builder.compact();
    }

    /* ===== Claims helpers ===== */
    private Claims parse(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }

    public String getEmail(String token) {
        return parse(token).getSubject();
    }

    public String getJti(String token) {
        Object v = parse(token).get("jti");
        return v == null ? null : v.toString();
    }

    public Boolean getFirstLoginClaim(String token) {
        Object v = parse(token).get("firstLogin");
        if (v == null) return Boolean.FALSE;
        return (v instanceof Boolean) ? (Boolean) v : Boolean.parseBoolean(v.toString());
    }

    /* ===== Validation ===== */
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

    public enum TokenValidationResult {
        VALID, EXPIRED, INVALID
    }
}
