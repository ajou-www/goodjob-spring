package com.www.goodjob.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService {

    private final StringRedisTemplate redisTemplate;

    // 토큰 저장 (TTL 단위: 일)
    public void saveToken(String email, String token, long days) {
        redisTemplate.opsForValue().set(buildKey(email), token, Duration.ofDays(days));
    }

    // 토큰 유효성 검증
    public boolean isTokenValid(String email, String token) {
        String stored = redisTemplate.opsForValue().get(buildKey(email));
        return stored != null && stored.equals(token);
    }

    // 토큰 삭제
    public void deleteToken(String email) {
        redisTemplate.delete(buildKey(email));
    }

    // Key 명세 통일
    private String buildKey(String email) {
        return "refresh:" + email;
    }
}
