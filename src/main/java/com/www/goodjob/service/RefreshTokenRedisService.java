package com.www.goodjob.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "refresh:";

    public void saveToken(String email, String refreshToken, long daysToExpire) {
        String key = PREFIX + email;
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofDays(daysToExpire));
    }

    public String getToken(String email) {
        return redisTemplate.opsForValue().get(PREFIX + email);
    }

    public void deleteToken(String email) {
        redisTemplate.delete(PREFIX + email);
    }

    public boolean isTokenValid(String email, String token) {
        String stored = getToken(email);
        return stored != null && stored.equals(token);
    }
}
