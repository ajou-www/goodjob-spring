package com.www.goodjob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService {

    private final StringRedisTemplate redis;

    /** 토큰 저장 (email+jti 별로 저장) */
    public void saveToken(String email, String jti, String token, long days) {
        String tokenKey = keyToken(email, jti);
        String indexKey = keyIndex(email);

        // 1) 토큰 값 저장 + TTL
        redis.opsForValue().set(tokenKey, token, Duration.ofDays(days));

        // 2) 인덱스 세트에 jti 추가 (세트 자체에도 TTL 갱신)
        redis.opsForSet().add(indexKey, jti);
        redis.expire(indexKey, Duration.ofDays(days));

        log.info("[REDIS] Saved RT email={}, jti={}, ttlDays={}", email, jti, days);
    }

    /** 토큰 유효성 검증 (email+jti 조합) */
    public boolean isTokenValid(String email, String jti, String token) {
        String tokenKey = keyToken(email, jti);
        String stored = redis.opsForValue().get(tokenKey);

        if (stored == null) {
            log.warn("[REDIS] No RT found for email={}, jti={}", email, jti);
            return false;
        }
        boolean ok = stored.equals(token);
        if (!ok) log.warn("[REDIS] RT mismatch for email={}, jti={}", email, jti);
        return ok;
    }

    /** 해당 기기/탭 로그아웃 */
    public void deleteToken(String email, String jti) {
        String tokenKey = keyToken(email, jti);
        String indexKey = keyIndex(email);

        Boolean del = redis.delete(tokenKey);
        Long removed = redis.opsForSet().remove(indexKey, jti);

        log.info("[REDIS] Delete RT email={}, jti={}, tokenDel={}, indexRemoved={}",
                email, jti, del, removed);
    }

    /** 전체 로그아웃/회원탈퇴: email의 모든 jti 삭제 */
    public void deleteAllTokens(String email) {
        String indexKey = keyIndex(email);
        Set<String> jtIs = redis.opsForSet().members(indexKey);
        if (jtIs == null || jtIs.isEmpty()) {
            log.warn("[REDIS] No RT index members for email={}", email);
            // 그래도 인덱스 키는 지워둔다
            redis.delete(indexKey);
            return;
        }

        int delCount = 0;
        for (String jti : jtIs) {
            String tokenKey = keyToken(email, jti);
            if (Boolean.TRUE.equals(redis.delete(tokenKey))) delCount++;
        }
        redis.delete(indexKey);
        log.info("[REDIS] Deleted {} RT keys and index for email={}", delCount, email);
    }

    /* ===== Keys ===== */
    private String keyToken(String email, String jti) {
        return "refresh:" + email + ":" + jti;
    }
    private String keyIndex(String email) {
        return "refresh:index:" + email;
    }
}
