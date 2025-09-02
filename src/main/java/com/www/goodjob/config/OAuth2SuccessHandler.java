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
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@RequiredArgsConstructor
@Component
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final RefreshTokenRedisService refreshTokenRedisService;

    // 쿠키 속성(운영/개발 프로파일에 맞춰 yml로 분리)
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

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        CustomOAuth2User customUser = (oAuth2User instanceof CustomOAuth2User)
                ? (CustomOAuth2User) oAuth2User
                : new CustomOAuth2User(oAuth2User);

        String email = customUser.getEmail();
        String name = customUser.getName();
        OAuthProvider provider = extractProvider(customUser);
        String oauthId = customUser.getOauthId(provider);

        // 최초 로그인 여부 판단 및 저장
        boolean isFirstLogin = !userRepository.existsByEmail(email);
        if (isFirstLogin) {
            User newUser = userRepository.save(User.builder()
                    .email(email)
                    .name(name)
                    .role(UserRole.USER)
                    .build());

            userOAuthRepository.save(UserOAuth.builder()
                    .user(newUser)
                    .provider(provider)
                    .oauthId(oauthId)
                    .build());

            log.info("[OAUTH] firstLogin = true for email={}", email);
        } else {
            log.info("[OAUTH] firstLogin = false for email={}", email);
        }

        // 1) RT 생성 (jti 포함) + Redis 저장(email+jti별)
        String refreshToken = jwtTokenProvider.generateRefreshToken(email, isFirstLogin);
        String jti = jwtTokenProvider.getJti(refreshToken); // 새로 발급된 jti 추출
        refreshTokenRedisService.saveToken(email, jti, refreshToken, 14);

        // 2) 쿠키에 RT 세팅 (도메인/보안 속성은 프로파일에 따라)
        ResponseCookie.ResponseCookieBuilder cb = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSameSite)
                .maxAge(Duration.ofDays(14));
        if (cookieDomain != null && !cookieDomain.isBlank()) cb.domain(cookieDomain);
        response.addHeader(HttpHeaders.SET_COOKIE, cb.build().toString());

        // 3) 프론트로 리다이렉션
        String encodedState = request.getParameter("state");
        String redirectUri = "https://www.goodjob.ai.kr/auth/callback";

        try {
            if (encodedState != null) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedState);
                String decoded = new String(decodedBytes, StandardCharsets.UTF_8).replaceAll("[\\r\\n]", "");
                if (isValidRedirectUri(decoded)) {
                    redirectUri = decoded;
                    log.info("[OAUTH] decoded redirect_uri from state: {}", redirectUri);
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("[OAUTH] failed to decode or validate state, fallback to default. state: {}", encodedState);
        }

        if (!response.isCommitted()) {
            response.sendRedirect(redirectUri);
        }
    }

    private boolean isValidRedirectUri(String uri) {
        return uri != null &&
                !uri.contains("\r") &&
                !uri.contains("\n") &&
                (uri.startsWith("https://localhost:5173") || uri.startsWith("https://www.goodjob.ai.kr"));
    }

    private OAuthProvider extractProvider(CustomOAuth2User user) {
        if (user.getAttributes().containsKey("provider")) {
            return OAuthProvider.valueOf(user.getAttributes().get("provider").toString().toUpperCase());
        }
        throw new IllegalStateException("OAuth provider not found in user attributes");
    }
}
