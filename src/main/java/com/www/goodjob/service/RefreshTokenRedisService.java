package com.www.goodjob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j // 로그 사용
@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService {

    private final StringRedisTemplate redisTemplate;

    // 토큰 저장 (TTL 단위: 일)
    public void saveToken(String email, String token, long days) {
        redisTemplate.opsForValue().set(buildKey(email), token, Duration.ofDays(days));
        log.info("[REDIS] Saved refresh token for email={}, ttl={}일", email, days);
    }

    // 토큰 유효성 검증
    public boolean isTokenValid(String email, String token) {
        String stored = redisTemplate.opsForValue().get(buildKey(email));

        if (stored == null) {
            log.warn("[REDIS] No refresh token found in Redis for email={}", email);
            return false;
        }

        boolean isValid = stored.equals(token);
        if (!isValid) {
            log.warn("[REDIS] Invalid refresh token request for email={} (mismatch)", email);
        }
        return isValid;
    }

    // 토큰 삭제
    public void deleteToken(String email) {
        Boolean deleted = redisTemplate.delete(buildKey(email));
        if (Boolean.TRUE.equals(deleted)) {
            log.info("[REDIS] Deleted refresh token for email={}", email);
        } else {
            log.warn("[REDIS] No token found to delete for email={}", email);
        }
    }

    // Key 명세 통일
    private String buildKey(String email) {
        return "refresh:" + email;
    }
}
