package com.www.goodjob.config;

import com.www.goodjob.domain.User;
import com.www.goodjob.domain.UserOAuth;
import com.www.goodjob.enums.OAuthProvider;
import com.www.goodjob.enums.UserRole;
import com.www.goodjob.repository.UserOAuthRepository;
import com.www.goodjob.repository.UserRepository;
import com.www.goodjob.security.CustomOAuth2User;
import com.www.goodjob.security.JwtTokenProvider;
import com.www.goodjob.service.RefreshTokenRedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
public class OAuth2SuccessHandler extends org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final RefreshTokenRedisService refreshTokenRedisService;

    @Value("${app.cookie.domain:}")
    private String cookieDomain;
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;
    @Value("${app.cookie.sameSite:None}")
    private String cookieSameSite;

    // 이메일 병합 허용 여부(기본 false = 병합 금지)
    @Value("${app.auth.link-accounts-by-email:false}")
    private boolean linkAccountsByEmail;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // 1) registrationId로 제공자 확정
        if (!(authentication instanceof OAuth2AuthenticationToken oAuthToken)) {
            log.error("[OAUTH] Unexpected authentication type: {}", authentication == null ? "null" : authentication.getClass());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unsupported authentication type");
            return;
        }
        String registrationId = oAuthToken.getAuthorizedClientRegistrationId(); // "google" | "kakao" | ...
        OAuthProvider provider = resolveProvider(registrationId);

        // 2) 프로바이더별 식별 정보 추출
        OAuth2User oAuth2User = (OAuth2User) oAuthToken.getPrincipal();
        CustomOAuth2User customUser = (oAuth2User instanceof CustomOAuth2User)
                ? (CustomOAuth2User) oAuth2User
                : new CustomOAuth2User(oAuth2User);

        String email   = customUser.getEmail();           // kakao: kakao_account.email (null 가능)
        String name    = customUser.getName();
        String oauthId = customUser.getOauthId(provider); // google=sub, kakao=id

        log.info("[OAUTH] provider={}, oauthId={}, email={}, linkByEmail={}", provider, oauthId, email, linkAccountsByEmail);

        // 3) 매핑 우선( provider+oauthId )
        Optional<UserOAuth> mappingOpt = userOAuthRepository.findByProviderAndOauthId(provider, oauthId);
        User user;

        if (mappingOpt.isPresent()) {
            // (A) 매핑 존재 → 해당 유저로 로그인
            user = mappingOpt.get().getUser();
            log.info("[OAUTH] mapping hit: userId={}, email={}", user.getId(), user.getEmail());

        } else {
            // (B) 매핑 없음 → 정책에 따라
            if (linkAccountsByEmail && email != null && !email.isBlank()) {
                // 옵션: 이메일로 병합 허용 시
                user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    user = createUser(email, name);
                    log.info("[OAUTH] created new user by email (link enabled): id={}, email={}", user.getId(), user.getEmail());
                } else {
                    log.info("[OAUTH] linked to existing user by email (link enabled): id={}, email={}", user.getId(), user.getEmail());
                }
            } else {
                // 기본: 이메일 병합 금지 → 항상 새 유저 생성 (email 충돌 시 대응)
                user = tryCreateUserStrict(email, name);
            }

            // 현재 provider 매핑 생성
            userOAuthRepository.save(UserOAuth.builder()
                    .user(user).provider(provider).oauthId(oauthId).build());
            log.info("[OAUTH] created mapping: userId={}, provider={}, oauthId={}", user.getId(), provider, oauthId);
        }

        // 4) RT 발급(jti 포함) + Redis 저장(email+jti 별)
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), false);
        String jti = jwtTokenProvider.getJti(refreshToken);
        refreshTokenRedisService.saveToken(user.getEmail(), jti, refreshToken, 14);

        // 5) 쿠키 세팅
        ResponseCookie.ResponseCookieBuilder cb = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true).secure(cookieSecure).path("/").sameSite(cookieSameSite)
                .maxAge(Duration.ofDays(14));
        if (cookieDomain != null && !cookieDomain.isBlank()) cb.domain(cookieDomain);
        response.addHeader(HttpHeaders.SET_COOKIE, cb.build().toString());

        // 6) 리다이렉트
        String redirectUri = resolveRedirectFromStateOrDefault(request.getParameter("state"));
        if (!response.isCommitted()) response.sendRedirect(redirectUri);
    }

    private OAuthProvider resolveProvider(String registrationId) {
        try {
            return OAuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 등록만 되었고 enum에 없으면 명확히 실패시켜 버그를 조기 노출
            throw new IllegalStateException("Unsupported provider registrationId=" + registrationId, e);
        }
    }

    /** 기본 병합 금지 정책에서 새 유저를 만든다. 이메일 유니크 제약이 있으면 충돌 처리. */
    private User tryCreateUserStrict(String email, String name) {
        try {
            return createUser(email, name);
        } catch (DataIntegrityViolationException dup) {
            // DB에 email UNIQUE가 걸려 있고 동일 이메일 유저가 이미 존재
            // 1) 진짜로 분리 계정을 원한다면: 스키마에서 email unique를 풀거나, provider별 가공 이메일 정책으로 변경 필요
            // 2) 당장 깨지지 않게 하려면: 기존 유저로 연결(일시적 폴백)
            log.warn("[OAUTH] email unique constraint hit (strict mode). Falling back to existing user link. email={}", email);
            return userRepository.findByEmail(email).orElseGet(() -> createUser(null, name)); // 그래도 없으면 email=null로 신규
        }
    }

    private User createUser(String email, String name) {
        return userRepository.save(User.builder()
                .email(email) // 스키마가 NOT NULL이면 여기서 null 금지. 필요 시 provider별 가공이메일 사용.
                .name(name)
                .role(UserRole.USER)
                .build());
    }

    private String resolveRedirectFromStateOrDefault(String encodedState) {
        String def = "https://www.goodjob.ai.kr/auth/callback";
        if (encodedState == null) return def;
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedState);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8).replaceAll("[\\r\\n]", "");
            if (isValidRedirectUri(decoded)) return decoded;
        } catch (IllegalArgumentException e) {
            log.warn("[OAUTH] state decode failed: {}", encodedState);
        }
        return def;
    }

    private boolean isValidRedirectUri(String uri) {
        return uri != null &&
                !uri.contains("\r") &&
                !uri.contains("\n") &&
                (uri.startsWith("https://localhost:5173") || uri.startsWith("https://www.goodjob.ai.kr"));
    }
}
