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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // 1) registrationId로 제공자 확정 (가장 신뢰가능)
        if (!(authentication instanceof OAuth2AuthenticationToken oAuthToken)) {
            log.error("[OAUTH] Unexpected authentication type: {}", authentication == null ? "null" : authentication.getClass());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unsupported authentication type");
            return;
        }
        String registrationId = oAuthToken.getAuthorizedClientRegistrationId(); // "google" / "kakao"
        OAuthProvider provider = OAuthProvider.valueOf(registrationId.toUpperCase());

        // 2) 유저 정보 추출
        OAuth2User oAuth2User = (OAuth2User) oAuthToken.getPrincipal();
        CustomOAuth2User customUser = (oAuth2User instanceof CustomOAuth2User)
                ? (CustomOAuth2User) oAuth2User
                : new CustomOAuth2User(oAuth2User);

        String email   = customUser.getEmail();                 // kakao는 kakao_account.email
        String name    = customUser.getName();
        String oauthId = customUser.getOauthId(provider);       // google=sub, kakao=id

        log.info("[OAUTH] provider={}, oauthId={}, email={}", provider, oauthId, email);

        // 3)  매핑 우선 조회: provider+oauthId로 User 찾기
        Optional<UserOAuth> mappingOpt = userOAuthRepository.findByProviderAndOauthId(provider, oauthId);
        User user;

        if (mappingOpt.isPresent()) {
            // (A) 매핑 이미 있음 → 해당 유저로 로그인
            user = mappingOpt.get().getUser();
            log.info("[OAUTH] mapping hit: userId={}, email={}", user.getId(), user.getEmail());

        } else {
            // (B) 매핑 없음 → email로 유저 연결하거나 신규 생성
            user = (email != null && !email.isBlank())
                    ? userRepository.findByEmail(email).orElse(null)
                    : null;

            final boolean isFirstLogin = (user == null);

            if (user == null) {
                // 신규 유저 생성
                user = userRepository.save(User.builder()
                        .email(email)             // kakao에서 이메일 동의 안했으면 null일 수도 있으니, 스키마 제약 확인 필요
                        .name(name)
                        .role(UserRole.USER)
                        .build());
                log.info("[OAUTH] created new user: id={}, email={}", user.getId(), user.getEmail());
            }

            // provider 매핑 생성 (멀티 프로바이더 지원)
            userOAuthRepository.save(UserOAuth.builder()
                    .user(user)
                    .provider(provider)
                    .oauthId(oauthId)
                    .build());

            log.info("[OAUTH] created mapping: userId={}, provider={}, oauthId={}", user.getId(), provider, oauthId);
        }

        // 4) RT 발급(jti 포함) + Redis 저장(email+jti 별)
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), false /* firstLogin은 굳이 쿠키 claim에 쓰지 않아도 됨 */);
        String jti = jwtTokenProvider.getJti(refreshToken);
        refreshTokenRedisService.saveToken(user.getEmail(), jti, refreshToken, 14);

        // 5) 쿠키 세팅(프로파일별 속성 반영)
        ResponseCookie.ResponseCookieBuilder cb = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSameSite)
                .maxAge(Duration.ofDays(14));
        if (cookieDomain != null && !cookieDomain.isBlank()) cb.domain(cookieDomain);
        response.addHeader(HttpHeaders.SET_COOKIE, cb.build().toString());

        // 6) 리다이렉트 (state 우선)
        String redirectUri = resolveRedirectFromStateOrDefault(request.getParameter("state"));
        if (!response.isCommitted()) {
            response.sendRedirect(redirectUri);
        }
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
