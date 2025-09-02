package com.www.goodjob.security;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final long ACCESS_TOKEN_VALID_TIME  = 1000L * 60 * 10;              // 10분
    private final long REFRESH_TOKEN_VALID_TIME = 1000L * 60 * 60 * 24 * 14;    // 14일

    private final String secretKey;

    public JwtTokenProvider(@Value("${jwt.secretKey:my-secret-key}") String secretKey) {
        this.secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    // ===== Access =====
    public String generateAccessToken(String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ACCESS_TOKEN_VALID_TIME))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    // ===== Refresh (신규 jti) =====
    public String generateRefreshToken(String email, boolean isFirstLogin) {
        String jti = UUID.randomUUID().toString();
        return buildRefresh(email, isFirstLogin, jti, REFRESH_TOKEN_VALID_TIME);
    }
    public String generateRefreshToken(String email) {
        return generateRefreshToken(email, false);
    }

    // ===== Refresh (기존 jti 유지) =====
    public String generateRefreshTokenWithExistingJti(String email, String jti) {
        // firstLogin 클레임은 재발급 시 굳이 쓸 필요 없으면 false/누락 둘 다 OK
        return buildRefresh(email, null, jti, REFRESH_TOKEN_VALID_TIME);
    }

    private String buildRefresh(String email, Boolean firstLogin, String jti, long validityMs) {
        long now = System.currentTimeMillis();
        JwtBuilder b = Jwts.builder()
                .setId(jti)                      // ★ jti 유지/부여
                .setSubject(email)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + validityMs))
                .signWith(SignatureAlgorithm.HS256, secretKey);

        if (firstLogin != null) b.claim("firstLogin", firstLogin);
        return b.compact();
    }

    // ===== 파서/검증 =====
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
    public boolean validateToken(String token) {
        return validateTokenDetailed(token) == TokenValidationResult.VALID;
    }

    public String getEmail(String token) {
        return Jwts.parser().setSigningKey(secretKey)
                .parseClaimsJws(token).getBody().getSubject();
    }

    public String getJti(String token) {
        return Jwts.parser().setSigningKey(secretKey)
                .parseClaimsJws(token).getBody().getId();
    }

    public Boolean getFirstLoginClaim(String token) {
        Object v = Jwts.parser().setSigningKey(secretKey)
                .parseClaimsJws(token).getBody().get("firstLogin");
        if (v == null) return null;
        return (v instanceof Boolean) ? (Boolean) v : Boolean.parseBoolean(v.toString());
    }

    public enum TokenValidationResult { VALID, EXPIRED, INVALID }
}
